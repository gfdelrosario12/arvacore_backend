package com.aquaflow.backend.infrastructure.security;

import com.aquaflow.backend.entity.DeviceAuthNonce;
import com.aquaflow.backend.entity.DeviceCredential;
import com.aquaflow.backend.entity.DeviceCredentialState;
import com.aquaflow.backend.infrastructure.exception.UnauthorizedException;
import com.aquaflow.backend.persistence.DeviceAuthNonceRepository;
import com.aquaflow.backend.persistence.DeviceCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceHmacValidator {

    public static final String PROTOCOL_VERSION = "ARVA-HMAC-V1";
    public static final String HEADER_NODE_ID = "X-Arva-Node-ID";
    public static final String HEADER_KEY_ID = "X-Arva-Key-ID";
    public static final String HEADER_TIMESTAMP = "X-Arva-Timestamp";
    public static final String HEADER_NONCE = "X-Arva-Nonce";
    public static final String HEADER_SIGNATURE = "X-Arva-Signature";

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 120; // 2 minutes

    private final DeviceCredentialRepository deviceCredentialRepository;
    private final DeviceAuthNonceRepository deviceAuthNonceRepository;

    public record DeviceAuthResult(
            boolean authenticated,
            String nodeId,
            String keyId,
            String role, // ROLE_DEVICE_BOOTSTRAP or ROLE_DEVICE_OPERATIONAL
            String errorCode,
            String errorMessage
    ) {
        public static DeviceAuthResult success(String nodeId, String keyId, String role) {
            return new DeviceAuthResult(true, nodeId, keyId, role, null, null);
        }

        public static DeviceAuthResult failure(String errorCode, String errorMessage) {
            return new DeviceAuthResult(false, null, null, null, errorCode, errorMessage);
        }
    }

    /**
     * Validates an incoming device request against the ARVA-HMAC-V1 contract.
     */
    @Transactional
    public DeviceAuthResult validateRequest(
            String httpMethod,
            String requestPath,
            String queryString,
            byte[] bodyBytes,
            String nodeIdHeader,
            String keyIdHeader,
            String timestampHeader,
            String nonceHeader,
            String signatureHeader
    ) {
        if (nodeIdHeader == null || nodeIdHeader.isBlank()) {
            return DeviceAuthResult.failure("DEVICE_UNKNOWN", "Missing " + HEADER_NODE_ID);
        }
        if (keyIdHeader == null || keyIdHeader.isBlank()) {
            return DeviceAuthResult.failure("DEVICE_SIGNATURE_INVALID", "Missing " + HEADER_KEY_ID);
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return DeviceAuthResult.failure("DEVICE_CLOCK_SKEW", "Missing " + HEADER_TIMESTAMP);
        }
        if (nonceHeader == null || nonceHeader.isBlank()) {
            return DeviceAuthResult.failure("DEVICE_REPLAY_DETECTED", "Missing " + HEADER_NONCE);
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return DeviceAuthResult.failure("DEVICE_SIGNATURE_INVALID", "Missing " + HEADER_SIGNATURE);
        }

        String nodeId = nodeIdHeader.trim();
        String keyId = keyIdHeader.trim();
        String nonce = nonceHeader.trim();
        String signature = signatureHeader.trim();

        // 1. Timestamp freshness validation
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return DeviceAuthResult.failure("DEVICE_CLOCK_SKEW", "Invalid timestamp format: " + timestampHeader);
        }

        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestampSeconds) > TIMESTAMP_TOLERANCE_SECONDS) {
            log.warn("Clock skew rejected for node {}: server={}, device={}, diff={}s (max allowed {}s)",
                    nodeId, nowSeconds, timestampSeconds, Math.abs(nowSeconds - timestampSeconds), TIMESTAMP_TOLERANCE_SECONDS);
            return DeviceAuthResult.failure("DEVICE_CLOCK_SKEW", "Timestamp outside acceptable window (+/- 120s)");
        }

        // 2. Nonce replay protection
        Optional<DeviceAuthNonce> existingNonce = deviceAuthNonceRepository.findByNodeIdAndNonce(nodeId, nonce);
        if (existingNonce.isPresent()) {
            log.warn("Replay attack detected for node {} with reused nonce {}", nodeId, nonce);
            return DeviceAuthResult.failure("DEVICE_REPLAY_DETECTED", "Replay detected: nonce has already been used");
        }

        // 3. Credential retrieval & authorization
        Optional<DeviceCredential> credentialOpt = deviceCredentialRepository.findByNodeId(nodeId);
        if (credentialOpt.isEmpty()) {
            log.warn("Unknown device attempted HMAC auth: {}", nodeId);
            return DeviceAuthResult.failure("DEVICE_UNKNOWN", "Device not registered: " + nodeId);
        }

        DeviceCredential credential = credentialOpt.get();
        if (credential.getCredentialState() == DeviceCredentialState.REVOKED) {
            log.warn("Revoked device attempted HMAC auth: {}", nodeId);
            return DeviceAuthResult.failure("DEVICE_CREDENTIAL_REVOKED", "Device credentials have been revoked for node " + nodeId);
        }

        String secret;
        String role;
        if (keyId.equalsIgnoreCase(credential.getBootstrapKeyId())) {
            secret = credential.getBootstrapSecret();
            role = "ROLE_DEVICE_BOOTSTRAP";
        } else if (credential.getOperationalKeyId() != null && keyId.equalsIgnoreCase(credential.getOperationalKeyId())) {
            boolean isCompletionHandshake = (requestPath != null && requestPath.endsWith("/complete"));
            if (!isCompletionHandshake && credential.getCredentialState() != DeviceCredentialState.OPERATIONAL) {
                return DeviceAuthResult.failure("DEVICE_NOT_PAIRED", "Operational key presented but device state is " + credential.getCredentialState());
            }
            secret = credential.getOperationalSecret();
            role = "ROLE_DEVICE_OPERATIONAL";
        } else {
            log.warn("Key ID mismatch for node {}: presented {} does not match bootstrap ({}) or operational ({})",
                    nodeId, keyId, credential.getBootstrapKeyId(), credential.getOperationalKeyId());
            return DeviceAuthResult.failure("DEVICE_SIGNATURE_INVALID", "Unrecognized key identifier for device");
        }

        if (secret == null || secret.isBlank()) {
            return DeviceAuthResult.failure("DEVICE_CREDENTIAL_REVOKED", "Configured secret is empty");
        }

        // 4. Canonical request construction
        String canonicalRequest = buildCanonicalRequest(
                httpMethod, requestPath, queryString, bodyBytes, timestampSeconds, nonce, nodeId, keyId
        );

        // 5. Signature verification
        String expectedSignature = computeHmacSha256(secret, canonicalRequest);
        if (!constantTimeSignatureMatch(signature, expectedSignature)) {
            log.warn("Invalid HMAC signature for node {} key {}. Canonical request:\n{}", nodeId, keyId, canonicalRequest);
            return DeviceAuthResult.failure("DEVICE_SIGNATURE_INVALID", "Signature mismatch");
        }

        // 6. Record nonce to prevent replay
        DeviceAuthNonce newNonce = DeviceAuthNonce.builder()
                .nodeId(nodeId)
                .nonce(nonce)
                .expiresAt(LocalDateTime.now().plusSeconds(TIMESTAMP_TOLERANCE_SECONDS * 2))
                .createdAt(LocalDateTime.now())
                .build();
        deviceAuthNonceRepository.save(newNonce);

        return DeviceAuthResult.success(nodeId, keyId, role);
    }

    public static String buildCanonicalRequest(
            String method,
            String path,
            String queryString,
            byte[] bodyBytes,
            long timestamp,
            String nonce,
            String nodeId,
            String keyId
    ) {
        String normalizedMethod = (method != null) ? method.trim().toUpperCase() : "GET";
        String normalizedPath = (path != null) ? path.trim() : "/";
        if (normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }

        String pathAndCanonicalQuery = normalizedPath;
        if (queryString != null && !queryString.isBlank()) {
            String sortedQuery = canonicalizeQueryString(queryString);
            if (!sortedQuery.isBlank()) {
                pathAndCanonicalQuery += "?" + sortedQuery;
            }
        }

        String bodyHash = sha256Hex(bodyBytes != null ? bodyBytes : new byte[0]);

        return PROTOCOL_VERSION + "\n" +
                normalizedMethod + "\n" +
                pathAndCanonicalQuery + "\n" +
                bodyHash + "\n" +
                timestamp + "\n" +
                nonce + "\n" +
                nodeId + "\n" +
                keyId;
    }

    public static String computeHmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256", e);
        }
    }

    private static boolean constantTimeSignatureMatch(String presented, String expected) {
        if (presented == null || expected == null) return false;
        String cleanPresented = presented.replace("=", "").trim();
        String cleanExpected = expected.replace("=", "").trim();
        return MessageDigest.isEqual(
                cleanPresented.getBytes(StandardCharsets.UTF_8),
                cleanExpected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String canonicalizeQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return "";
        String[] pairs = rawQuery.split("&");
        Arrays.sort(pairs);
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs) {
            if (pair.isBlank()) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(pair);
        }
        return sb.toString();
    }
}
