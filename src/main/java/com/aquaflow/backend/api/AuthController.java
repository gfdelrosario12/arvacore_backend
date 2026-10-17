package com.aquaflow.backend.api;

import com.aquaflow.backend.dto.request.LoginRequest;
import com.aquaflow.backend.dto.request.RefreshTokenRequest;
import com.aquaflow.backend.dto.request.RegisterRequest;
import com.aquaflow.backend.dto.response.AuthResponse;
import com.aquaflow.backend.dto.response.UserResponse;
import com.aquaflow.backend.domain.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/v1/auth/register");
        UserResponse response = userService.registerUser(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/v1/auth/login");
        AuthResponse response = userService.authenticate(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /api/v1/auth/refresh");
        AuthResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(java.security.Principal principal) {
        log.info("GET /api/v1/auth/me");
        if (principal == null || principal.getName() == null) {
            throw new com.aquaflow.backend.infrastructure.exception.UnauthorizedException("Unauthenticated");
        }
        return ResponseEntity.ok(userService.getUserByUsername(principal.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<java.util.Map<String, String>> logout(java.security.Principal principal) {
        log.info("POST /api/v1/auth/logout for user {}", principal != null ? principal.getName() : "anonymous");
        return ResponseEntity.ok(java.util.Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String username) {
        log.info("GET /api/v1/auth/{}", username);
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }
}