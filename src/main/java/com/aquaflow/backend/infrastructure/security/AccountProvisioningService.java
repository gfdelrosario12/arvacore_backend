package com.aquaflow.backend.infrastructure.security;

import com.aquaflow.backend.domain.AuditLogService;
import com.aquaflow.backend.entity.User;
import com.aquaflow.backend.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Safe, idempotent provisioning and reconciliation service for baseline ArvaCore accounts.
 * Ensures the 6 required accounts exist with exact identities, normalized email addresses,
 * and explicit roles without overwriting custom or rotated passwords.
 */
@Component
public class AccountProvisioningService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountProvisioningService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public record BaselineAccount(String username, String email, String role) {}

    public static final List<BaselineAccount> BASELINE_ACCOUNTS = List.of(
            new BaselineAccount("admin", "admin@arvacore.io", "ROLE_ADMIN"),
            new BaselineAccount("Gladwin", "gladwin@aquaflow.io", "ROLE_ADMIN"),
            new BaselineAccount("Pranz", "pranz@aquaflow.io", "ROLE_OPERATOR"),
            new BaselineAccount("Jenice", "jenice@aquaflow.io", "ROLE_SUPERVISOR"),
            new BaselineAccount("MrFarmer", "mr.farmer@aquaflow.io", "ROLE_USER"),
            new BaselineAccount("MsFarmer", "ms.farmer@aquaflow.io", "ROLE_USER")
    );

    public AccountProvisioningService(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        provisionAccounts();
    }

    @Transactional
    public void provisionAccounts() {
        log.info("Starting baseline account reconciliation and verification...");

        // 1. Remove redundant seed accounts if they still exist unassociated
        List<String> obsoleteUsernames = List.of("farmer_m", "farmer_f", "operator");
        for (String obsolete : obsoleteUsernames) {
            userRepository.findByUsername(obsolete).ifPresent(user -> {
                if ("farmer_m@aquaflow.io".equalsIgnoreCase(user.getEmail()) ||
                    "farmer_f@aquaflow.io".equalsIgnoreCase(user.getEmail()) ||
                    "operator@arvacore.io".equalsIgnoreCase(user.getEmail())) {
                    log.info("Removing redundant seed duplicate row '{}' ({})", user.getUsername(), user.getEmail());
                    userRepository.delete(user);
                }
            });
        }

        // 2. Provision or reconcile the 6 required accounts
        for (BaselineAccount baseline : BASELINE_ACCOUNTS) {
            String normEmail = baseline.email().trim().toLowerCase();
            String normUsername = baseline.username().trim();

            Optional<User> existing = userRepository.findByEmailIgnoreCase(normEmail);
            if (existing.isEmpty()) {
                existing = userRepository.findByUsernameIgnoreCase(normUsername);
            }

            if (existing.isPresent()) {
                User user = existing.get();
                boolean changed = false;

                // Reconcile role if mismatched
                if (!baseline.role().equals(user.getRole())) {
                    String oldRole = user.getRole();
                    user.setRole(baseline.role());
                    changed = true;
                    log.info("Reconciled role for account {} from {} to {}", user.getUsername(), oldRole, baseline.role());
                }

                // Reconcile username casing or canonical form
                if (!normUsername.equals(user.getUsername())) {
                    user.setUsername(normUsername);
                    changed = true;
                }

                // Reconcile email normalization
                if (!normEmail.equals(user.getEmail())) {
                    user.setEmail(normEmail);
                    changed = true;
                }

                if (!user.isEnabled()) {
                    user.setEnabled(true);
                    changed = true;
                }

                if (changed) {
                    userRepository.save(user);
                    try {
                        auditLogService.logSystemEvent("ACCOUNT_RECONCILED", "SYSTEM", "USER", user.getId().toString(), null, null, "RECONCILED", Map.of("username", user.getUsername(), "role", user.getRole()));
                    } catch (Exception e) {
                        log.debug("Audit log recording skipped: {}", e.getMessage());
                    }
                }
            } else {
                // Create missing account with initial password123
                User newUser = User.builder()
                        .username(normUsername)
                        .email(normEmail)
                        .password(passwordEncoder.encode("password123"))
                        .role(baseline.role())
                        .enabled(true)
                        .createdAt(LocalDateTime.now())
                        .build();
                User saved = userRepository.save(newUser);
                log.info("Provisioned baseline account '{}' with role {}", saved.getUsername(), saved.getRole());
                try {
                    auditLogService.logSystemEvent("ACCOUNT_PROVISIONED", "SYSTEM", "USER", saved.getId().toString(), null, null, "PROVISIONED", Map.of("username", saved.getUsername(), "role", saved.getRole()));
                } catch (Exception e) {
                    log.debug("Audit log recording skipped: {}", e.getMessage());
                }
            }
        }

        log.info("Baseline account reconciliation and verification complete.");
    }
}

