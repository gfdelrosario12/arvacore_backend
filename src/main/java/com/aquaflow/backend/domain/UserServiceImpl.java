package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.RegisterRequest;
import com.aquaflow.backend.dto.request.UserRequest;
import com.aquaflow.backend.dto.response.AuthResponse;
import com.aquaflow.backend.dto.response.UserResponse;
import com.aquaflow.backend.entity.User;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.UnauthorizedException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.infrastructure.util.JwtUtil;
import com.aquaflow.backend.persistence.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger log =
            LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final com.aquaflow.backend.infrastructure.security.CustomUserDetailsService customUserDetailsService;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AlertService alertService;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            com.aquaflow.backend.infrastructure.security.CustomUserDetailsService customUserDetailsService,
            AuditLogService auditLogService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.customUserDetailsService = customUserDetailsService;
        this.auditLogService = auditLogService;
    }

    @Override
    public UserResponse registerUser(UserRequest request) {

        log.info("Registering user: {}", request.getUsername());

        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String normalizedUsername = request.getUsername() != null ? request.getUsername().trim() : "";

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ValidationException(
                    "Username already exists",
                    "USERNAME_DUPLICATE"
            );
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ValidationException(
                    "Email already exists",
                    "EMAIL_DUPLICATE"
            );
        }

        String assignedRole = "ROLE_USER";
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String requestedRole = com.aquaflow.backend.infrastructure.security.CustomUserDetailsService.normalizeRoleAuthority(request.getRole());
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isAdmin) {
                assignedRole = requestedRole;
            } else if (!requestedRole.equals("ROLE_USER")) {
                throw new com.aquaflow.backend.infrastructure.exception.UnauthorizedException("Access denied: only administrators can assign privileged roles");
            }
        }

        User user = User.builder()
                .username(normalizedUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .email(normalizedEmail)
                .role(assignedRole)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        try {
            auditLogService.logSystemEvent("USER_REGISTERED", saved.getUsername(), "USER", saved.getId().toString(), null, null, "REGISTERED", java.util.Map.of("role", saved.getRole(), "email", saved.getEmail()));
        } catch (Exception e) {
            log.debug("Audit log recording skipped: {}", e.getMessage());
        }
        if (alertService != null) {
            try {
                alertService.createNotification(
                        "USER_REGISTERED",
                        "User Account Registered: " + saved.getUsername(),
                        String.format("User account '%s' (%s) was provisioned with role %s.",
                                saved.getUsername(), saved.getEmail(), saved.getRole()),
                        "INFO",
                        "hardware",
                        null,
                        1L,
                        null,
                        null,
                        saved.getUsername(),
                        "System Administration",
                        "ENTIRE FIELD",
                        null,
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }

        log.info("User registered: {}", saved.getUsername());

        return DtoMapper.toUserResponse(saved);
    }

    @Override
    public UserResponse registerUser(RegisterRequest request) {

        UserRequest userRequest = new UserRequest();

        userRequest.setUsername(request.getUsername());
        userRequest.setPassword(request.getPassword());
        userRequest.setEmail(request.getEmail());
        userRequest.setRole("ROLE_USER");

        return registerUser(userRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id
                        )
                );

        return DtoMapper.toUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {

        User user = customUserDetailsService.findUserByIdentifier(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + username
                        )
                );

        return DtoMapper.toUserResponse(user);
    }

    @Override
    public AuthResponse authenticate(String identifier, String password) {

        User user = customUserDetailsService.findUserByIdentifier(identifier).orElse(null);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user != null ? user.getUsername() : identifier,
                            password
                    )
            );
        } catch (BadCredentialsException e) {
            String actor = user != null ? user.getUsername() : identifier;
            String entityId = user != null ? user.getId().toString() : "UNKNOWN";
            try {
                auditLogService.logSystemEvent("AUTH_FAILED", actor, "USER", entityId, null, null, "FAILED", java.util.Map.of("reason", "Invalid credentials"));
            } catch (Exception ex) {
                log.debug("Audit log recording skipped: {}", ex.getMessage());
            }
            throw new UnauthorizedException("Invalid credentials");
        }

        if (user == null) {
            user = customUserDetailsService.findUserByIdentifier(identifier)
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }

        try {
            auditLogService.logSystemEvent("AUTH_SUCCESS", user.getUsername(), "USER", user.getId().toString(), null, null, "AUTHENTICATED", java.util.Map.of("email", user.getEmail(), "role", user.getRole()));
        } catch (Exception ex) {
            log.debug("Audit log recording skipped: {}", ex.getMessage());
        }

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("role", user.getRole());
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        claims.put("username", user.getUsername());
        claims.put("fieldId", "1");

        String accessToken = jwtUtil.generateToken(user.getUsername(), claims);
        String refreshToken = jwtUtil.generateToken(user.getUsername() + "-refresh");

        log.info("User authenticated: {}", user.getUsername());

        UserResponse userResponse = DtoMapper.toUserResponse(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400000L)
                .user(userResponse)
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {

        String username = jwtUtil.extractUsername(refreshToken);

        if (username != null && username.contains("-refresh")) {

            String realUsername = username.replace("-refresh", "");

            User user = customUserDetailsService.findUserByIdentifier(realUsername)
                    .orElseThrow(() -> new UnauthorizedException("User not found for refresh token"));

            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("role", user.getRole());
            claims.put("userId", user.getId().toString());
            claims.put("email", user.getEmail());
            claims.put("username", user.getUsername());
            claims.put("fieldId", "1");

            String newAccessToken = jwtUtil.generateToken(user.getUsername(), claims);

            log.info("Token refreshed for user: {}", user.getUsername());

            UserResponse userResponse = DtoMapper.toUserResponse(user);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(86400000L)
                    .user(userResponse)
                    .build();
        }

        throw new UnauthorizedException("Invalid refresh token");
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String callerName = auth != null ? auth.getName() : null;

        if (!isAdmin) {
            // Non-admin can only update their own profile and cannot escalate roles
            if (callerName == null || !callerName.equals(user.getUsername())) {
                throw new UnauthorizedException("Access denied: cannot update another user's account");
            }
            if (request.getRole() != null && !request.getRole().equals(user.getRole())) {
                throw new UnauthorizedException("Access denied: only administrators can change user roles");
            }
        }

        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : user.getEmail();
        String normalizedUsername = request.getUsername() != null ? request.getUsername().trim() : user.getUsername();

        if (!normalizedEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ValidationException("Email already in use", "EMAIL_DUPLICATE");
        }
        if (!normalizedUsername.equalsIgnoreCase(user.getUsername()) && userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ValidationException("Username already in use", "USERNAME_DUPLICATE");
        }

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        if (isAdmin && request.getRole() != null && !request.getRole().isBlank()) {
            user.setRole(com.aquaflow.backend.infrastructure.security.CustomUserDetailsService.normalizeRoleAuthority(request.getRole()));
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);
        try {
            auditLogService.logSystemEvent("USER_UPDATED", callerName != null ? callerName : "SYSTEM", "USER", id.toString(), null, null, "UPDATED", java.util.Map.of("username", saved.getUsername(), "role", saved.getRole()));
        } catch (Exception e) {
            log.debug("Audit log recording skipped: {}", e.getMessage());
        }
        if (alertService != null) {
            try {
                alertService.createNotification(
                        "USER_UPDATED",
                        "User Account Updated: " + saved.getUsername(),
                        String.format("User account '%s' profile/permissions were updated by %s (role: %s).",
                                saved.getUsername(), callerName != null ? callerName : "SYSTEM", saved.getRole()),
                        "INFO",
                        "hardware",
                        null,
                        1L,
                        null,
                        null,
                        callerName != null ? callerName : "SYSTEM",
                        "System Administration",
                        "ENTIRE FIELD",
                        null,
                        null,
                        0,
                        null
                );
            } catch (Exception ignored) {}
        }

        log.info("User updated: {}", id);

        return DtoMapper.toUserResponse(saved);
    }

    @Override
    public void deleteUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String callerName = auth != null ? auth.getName() : "SYSTEM";

        userRepository.deleteById(id);
        try {
            auditLogService.logSystemEvent("USER_DELETED", callerName, "USER", id.toString(), null, null, "DELETED", java.util.Map.of("username", user.getUsername(), "email", user.getEmail()));
        } catch (Exception e) {
            log.debug("Audit log recording skipped: {}", e.getMessage());
        }

        log.info("User deleted: {}", id);
    }
}