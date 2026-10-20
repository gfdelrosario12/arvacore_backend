package com.aquaflow.backend.infrastructure.security;

import com.aquaflow.backend.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.util.List;

@Component
public class WebhookAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookAuthenticationFilter.class);

    @Value("${aquaflow.lora.webhook-secret:your-webhook-secret}")
    private String configuredWebhookSecret;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/v1/lora/webhook") || uri.startsWith("/api/v1/chirpstack/webhook"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String secretHeader = request.getHeader("X-Webhook-Secret");
        if (secretHeader == null || secretHeader.isBlank()) {
            secretHeader = request.getHeader("X-Downlink-Secret");
        }

        if (secretHeader != null && secretHeader.equals(configuredWebhookSecret)) {
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    "LORA_WEBHOOK",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_EDGE_NODE"), new SimpleGrantedAuthority("ROLE_OPERATOR"))
            );
            SecurityContextHolder.getContext().setAuthentication(authToken);
            filterChain.doFilter(request, response);
        } else {
            log.warn("Unauthorized webhook request to {} - invalid or missing webhook secret header", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .errorCode("UNAUTHORIZED")
                    .message("Invalid or missing webhook authentication secret header")
                    .details(request.getRequestURI())
                    .build();

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
        }
    }
}
