package com.aquaflow.backend.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    private final DeviceHmacValidator deviceHmacValidator;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/pairing") && !uri.startsWith("/api/v1/telemetry");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String signature = request.getHeader(DeviceHmacValidator.HEADER_SIGNATURE);
        String nodeId = request.getHeader(DeviceHmacValidator.HEADER_NODE_ID);

        // If no device signature header is present, fall through to JWT / Gateway auth
        if (signature == null || signature.isBlank() || nodeId == null || nodeId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        String keyId = wrappedRequest.getHeader(DeviceHmacValidator.HEADER_KEY_ID);
        String timestamp = wrappedRequest.getHeader(DeviceHmacValidator.HEADER_TIMESTAMP);
        String nonce = wrappedRequest.getHeader(DeviceHmacValidator.HEADER_NONCE);

        DeviceHmacValidator.DeviceAuthResult authResult = deviceHmacValidator.validateRequest(
                wrappedRequest.getMethod(),
                wrappedRequest.getRequestURI(),
                wrappedRequest.getQueryString(),
                wrappedRequest.getCachedBody(),
                nodeId,
                keyId,
                timestamp,
                nonce,
                signature
        );

        if (!authResult.authenticated()) {
            log.warn("Device HMAC authentication rejected for node {} on {}: [{}] {}",
                    nodeId, wrappedRequest.getRequestURI(), authResult.errorCode(), authResult.errorMessage());

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = Map.of(
                    "status", 401,
                    "errorCode", authResult.errorCode(),
                    "message", authResult.errorMessage(),
                    "error", Map.of(
                            "code", authResult.errorCode(),
                            "message", authResult.errorMessage(),
                            "requestId", nonce != null ? nonce : "unknown"
                    )
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return;
        }

        // Authentication succeeded: set device principal and authorities in SecurityContext
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                authResult.nodeId(),
                null,
                List.of(new SimpleGrantedAuthority(authResult.role()))
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);

        wrappedRequest.setAttribute("X-Device-Node-Id", authResult.nodeId());
        wrappedRequest.setAttribute("X-Device-Key-Id", authResult.keyId());
        wrappedRequest.setAttribute("X-Device-Role", authResult.role());

        filterChain.doFilter(wrappedRequest, response);
    }
}

