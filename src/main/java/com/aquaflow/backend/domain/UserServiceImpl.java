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

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public UserResponse registerUser(UserRequest request) {

        log.info("Registering user: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException(
                    "Username already exists",
                    "USERNAME_DUPLICATE"
            );
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException(
                    "Email already exists",
                    "EMAIL_DUPLICATE"
            );
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(
                        request.getRole() != null
                                ? request.getRole()
                                : "ROLE_USER"
                )
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);

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

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + username
                        )
                );

        return DtoMapper.toUserResponse(user);
    }

    @Override
    public AuthResponse authenticate(String username, String password) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username,
                            password
                    )
            );
        } catch (BadCredentialsException e) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userRepository.findByUsername(username)
                .orElse(null);

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        if (user != null) {
            claims.put("role", user.getRole());
            claims.put("fieldId", "1");
        }

        String accessToken = jwtUtil.generateToken(username, claims);
        String refreshToken =
                jwtUtil.generateToken(username + "-refresh");

        log.info("User authenticated: {}", username);

        UserResponse userResponse = user != null ? DtoMapper.toUserResponse(user) : null;

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

            String realUsername =
                    username.replace("-refresh", "");

            User user = userRepository.findByUsername(realUsername)
                    .orElse(null);

            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            if (user != null) {
                claims.put("role", user.getRole());
                claims.put("fieldId", "1");
            }

            String newAccessToken =
                    jwtUtil.generateToken(realUsername, claims);

            log.info(
                    "Token refreshed for user: {}",
                    realUsername
            );

            UserResponse userResponse = user != null ? DtoMapper.toUserResponse(user) : null;

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
    public UserResponse updateUser(
            Long id,
            UserRequest request) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id
                        )
                );

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        User saved = userRepository.save(user);

        log.info("User updated: {}", id);

        return DtoMapper.toUserResponse(saved);
    }

    @Override
    public void deleteUser(Long id) {

        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "User not found with id: " + id
            );
        }

        userRepository.deleteById(id);

        log.info("User deleted: {}", id);
    }
}