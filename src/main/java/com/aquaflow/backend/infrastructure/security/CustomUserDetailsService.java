package com.aquaflow.backend.infrastructure.security;

import com.aquaflow.backend.entity.User;
import com.aquaflow.backend.persistence.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        String authority = normalizeRoleAuthority(user.getRole());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authority)
                .disabled(!user.isEnabled())
                .build();
    }

    /**
     * Resolves a user by username, email, or canonical login alias.
     * Prevents ambiguity by resolving farmer_m and farmer_f to their respective accounts.
     */
    public java.util.Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return java.util.Optional.empty();
        }
        String trimmed = identifier.trim();

        // 1. Check known login aliases
        if ("farmer_m".equalsIgnoreCase(trimmed)) {
            java.util.Optional<User> user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("MrFarmer", "mr.farmer@aquaflow.io");
            if (user.isPresent()) return user;
        } else if ("farmer_f".equalsIgnoreCase(trimmed)) {
            java.util.Optional<User> user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("MsFarmer", "ms.farmer@aquaflow.io");
            if (user.isPresent()) return user;
        }

        // 2. Query by username or email case-insensitively
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(trimmed, trimmed);
    }

    /**
     * Normalizes a role or authority string to ensure standard ROLE_ prefixing
     * without duplicate prefixes or illegal argument exceptions.
     */
    public static String normalizeRoleAuthority(String role) {
        if (role == null || role.isBlank()) {
            return "ROLE_VIEWER";
        }
        String trimmed = role.trim();
        return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
    }
}