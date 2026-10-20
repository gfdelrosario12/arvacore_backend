package com.aquaflow.backend.security;

import com.aquaflow.backend.entity.DeviceAuthNonce;
import com.aquaflow.backend.entity.DeviceCredential;
import com.aquaflow.backend.entity.DeviceCredentialState;
import com.aquaflow.backend.infrastructure.security.DeviceHmacValidator;
import com.aquaflow.backend.persistence.DeviceAuthNonceRepository;
import com.aquaflow.backend.persistence.DeviceCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceHmacValidatorTest {

    @Mock
    private DeviceCredentialRepository credentialRepository;

    @Mock
    private DeviceAuthNonceRepository nonceRepository;

    @InjectMocks
    private DeviceHmacValidator validator;

    private final String nodeId = "ARVANODE-001";
    private final String bootstrapKeyId = "bootstrap-ARVANODE-001";
    private final String bootstrapSecret = "dev_sec_e62959d17ffe8ad6f98246918bd61fa4e87b7b8963cf44cc15cb5c18";
    private final String operationalKeyId = "oper-ARVANODE-001-1792500000";
    private final String operationalSecret = "oper_sec_99999999999999999999999999999999999999999999999999999999";

    private DeviceCredential bootstrapCredential;
    private DeviceCredential operationalCredential;

    @BeforeEach
    void setUp() {
        bootstrapCredential = DeviceCredential.builder()
                .id(1L)
                .nodeId(nodeId)
                .bootstrapKeyId(bootstrapKeyId)
                .bootstrapSecret(bootstrapSecret)
                .credentialState(DeviceCredentialState.BOOTSTRAP)
                .installationId("default")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        operationalCredential = DeviceCredential.builder()
                .id(1L)
                .nodeId(nodeId)
                .bootstrapKeyId(bootstrapKeyId)
                .bootstrapSecret(bootstrapSecret)
                .operationalKeyId(operationalKeyId)
                .operationalSecret(operationalSecret)
                .credentialState(DeviceCredentialState.OPERATIONAL)
                .installationId("default")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Valid bootstrap HMAC signature authenticates successfully")
    void testValidBootstrapHmacSignature() {
        when(credentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(bootstrapCredential));
        when(nonceRepository.findByNodeIdAndNonce(eq(nodeId), any())).thenReturn(Optional.empty());

        long timestamp = Instant.now().getEpochSecond();
        String nonce = "nonce-test-001";
        String method = "POST";
        String path = "/api/v1/pairing/requests";
        byte[] body = "{\"nodeId\":\"ARVANODE-001\"}".getBytes(StandardCharsets.UTF_8);

        String canonicalRequest = DeviceHmacValidator.buildCanonicalRequest(
                method, path, null, body, timestamp, nonce, nodeId, bootstrapKeyId
        );
        String signature = DeviceHmacValidator.computeHmacSha256(bootstrapSecret, canonicalRequest);

        var result = validator.validateRequest(
                method, path, null, body, nodeId, bootstrapKeyId,
                String.valueOf(timestamp), nonce, signature
        );

        assertTrue(result.authenticated());
        assertEquals(nodeId, result.nodeId());
        assertEquals("ROLE_DEVICE_BOOTSTRAP", result.role());
        verify(nonceRepository, times(1)).save(any(DeviceAuthNonce.class));
    }

    @Test
    @DisplayName("Tampered request body causes signature verification to fail")
    void testTamperedRequestBodyRejection() {
        when(credentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(bootstrapCredential));
        when(nonceRepository.findByNodeIdAndNonce(eq(nodeId), any())).thenReturn(Optional.empty());

        long timestamp = Instant.now().getEpochSecond();
        String nonce = "nonce-test-002";
        byte[] originalBody = "{\"nodeId\":\"ARVANODE-001\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedBody = "{\"nodeId\":\"ARVANODE-HACKED\"}".getBytes(StandardCharsets.UTF_8);

        String canonicalRequest = DeviceHmacValidator.buildCanonicalRequest(
                "POST", "/api/v1/pairing/requests", null, originalBody, timestamp, nonce, nodeId, bootstrapKeyId
        );
        String signature = DeviceHmacValidator.computeHmacSha256(bootstrapSecret, canonicalRequest);

        var result = validator.validateRequest(
                "POST", "/api/v1/pairing/requests", null, tamperedBody, nodeId, bootstrapKeyId,
                String.valueOf(timestamp), nonce, signature
        );

        assertFalse(result.authenticated());
        assertEquals("DEVICE_SIGNATURE_INVALID", result.errorCode());
    }

    @Test
    @DisplayName("Expired timestamp (> 120s skew) is rejected")
    void testClockSkewRejection() {
        long expiredTimestamp = Instant.now().getEpochSecond() - 300; // 5 minutes in the past
        String nonce = "nonce-test-003";

        var result = validator.validateRequest(
                "POST", "/api/v1/pairing/requests", null, new byte[0], nodeId, bootstrapKeyId,
                String.valueOf(expiredTimestamp), nonce, "dummy-signature"
        );

        assertFalse(result.authenticated());
        assertEquals("DEVICE_CLOCK_SKEW", result.errorCode());
        verify(credentialRepository, never()).findByNodeId(any());
    }

    @Test
    @DisplayName("Replayed nonce is rejected")
    void testReplayAttackRejection() {
        long timestamp = Instant.now().getEpochSecond();
        String nonce = "nonce-replayed";

        when(nonceRepository.findByNodeIdAndNonce(nodeId, nonce)).thenReturn(Optional.of(
                DeviceAuthNonce.builder().nodeId(nodeId).nonce(nonce).expiresAt(LocalDateTime.now().plusMinutes(5)).build()
        ));

        var result = validator.validateRequest(
                "POST", "/api/v1/pairing/requests", null, new byte[0], nodeId, bootstrapKeyId,
                String.valueOf(timestamp), nonce, "dummy-signature"
        );

        assertFalse(result.authenticated());
        assertEquals("DEVICE_REPLAY_DETECTED", result.errorCode());
    }

    @Test
    @DisplayName("Revoked credential cannot authenticate")
    void testRevokedCredentialRejection() {
        DeviceCredential revoked = DeviceCredential.builder()
                .nodeId(nodeId)
                .bootstrapKeyId(bootstrapKeyId)
                .bootstrapSecret(bootstrapSecret)
                .credentialState(DeviceCredentialState.REVOKED)
                .build();

        when(credentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(revoked));
        when(nonceRepository.findByNodeIdAndNonce(eq(nodeId), any())).thenReturn(Optional.empty());

        long timestamp = Instant.now().getEpochSecond();
        var result = validator.validateRequest(
                "POST", "/api/v1/pairing/requests", null, new byte[0], nodeId, bootstrapKeyId,
                String.valueOf(timestamp), "nonce-004", "dummy-sig"
        );

        assertFalse(result.authenticated());
        assertEquals("DEVICE_CREDENTIAL_REVOKED", result.errorCode());
    }

    @Test
    @DisplayName("Valid operational credential signs telemetry request")
    void testValidOperationalHmacSignature() {
        when(credentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(operationalCredential));
        when(nonceRepository.findByNodeIdAndNonce(eq(nodeId), any())).thenReturn(Optional.empty());

        long timestamp = Instant.now().getEpochSecond();
        String nonce = "nonce-oper-001";
        String method = "POST";
        String path = "/api/v1/telemetry/uplink/" + nodeId;
        byte[] body = "{\"soilMoisturePercent\":24.5}".getBytes(StandardCharsets.UTF_8);

        String canonicalRequest = DeviceHmacValidator.buildCanonicalRequest(
                method, path, null, body, timestamp, nonce, nodeId, operationalKeyId
        );
        String signature = DeviceHmacValidator.computeHmacSha256(operationalSecret, canonicalRequest);

        var result = validator.validateRequest(
                method, path, null, body, nodeId, operationalKeyId,
                String.valueOf(timestamp), nonce, signature
        );

        assertTrue(result.authenticated());
        assertEquals("ROLE_DEVICE_OPERATIONAL", result.role());
    }
}

