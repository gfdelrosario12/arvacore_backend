package com.aquaflow.backend.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates the ArvaCore field gateway on telemetry ingestion routes.
 *
 * <p>Per the thesis architecture, ArvaNodes (ESP32, Wi-Fi) send readings to a gateway that forwards
 * them to the application server. The gateway presents {@code X-ArvaCore-Gateway-Key}. The key is
 * configured only server-side ({@code ARVACORE_GATEWAY_INGEST_KEY}); it is never shipped to ArvaNet.
 * Requests without the header fall through to normal JWT authentication. A present-but-wrong key is
 * rejected with 401. Node identity is still resolved against the registry (unknown nodes → 404).</p>
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-ArvaCore-Gateway-Key";
    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);

    @Value("${arvacore.gateway.ingest-key:}")
    private String configuredKey;

    void setConfiguredKey(String key) { this.configuredKey = key; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/telemetry/")
                && !uri.startsWith("/api/v1/pairing/request")
                && !uri.startsWith("/api/v1/pairing/status")
                && !uri.startsWith("/api/v1/irrigation/pending-command/")
                && !uri.startsWith("/api/v1/irrigation/ack/")
                && !uri.startsWith("/api/v1/gates/pending-command/")
                && !uri.startsWith("/api/v1/gates/ack/")
                && !uri.startsWith("/api/v1/gates/telemetry/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented == null) {
            chain.doFilter(request, response);
            return;
        }
        if (configuredKey == null || configuredKey.isBlank() || !constantTimeEquals(presented, configuredKey)) {
            log.warn("Rejected gateway ingestion to {}: invalid gateway key", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Invalid gateway key\"}");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ARVACORE_GATEWAY", null, List.of(new SimpleGrantedAuthority("ROLE_GATEWAY"))));
        chain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}

