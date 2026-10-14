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
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String authority = normalizeRoleAuthority(user.getRole());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authority)
                .disabled(!user.isEnabled())
                .build();
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