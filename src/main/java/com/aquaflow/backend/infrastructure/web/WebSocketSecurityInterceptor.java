package com.aquaflow.backend.infrastructure.web;

import com.aquaflow.backend.infrastructure.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
public class WebSocketSecurityInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSecurityInterceptor.class);

    private final JwtUtil jwtUtil;

    public WebSocketSecurityInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        String token = extractToken(request);

        if (token != null && !token.isBlank()) {
            try {
                String username = jwtUtil.extractUsername(token);
                if (username != null && !username.isBlank()) {
                    attributes.put("username", username);
                    log.info("WebSocket handshake authorized for user: {}", username);
                    return true;
                }
            } catch (Exception e) {
                log.warn("Invalid JWT token during WebSocket handshake: {}", e.getMessage());
            }
        }

        // If no token is provided or validation fails, reject with 401
        log.warn("Unauthorized WebSocket handshake attempt to: {}", request.getURI());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        URI uri = request.getURI();
        String query = uri.getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
                if (param.startsWith("access_token=")) {
                    return param.substring(13);
                }
            }
        }

        return null;
    }
}

