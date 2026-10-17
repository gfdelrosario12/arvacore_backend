package com.aquaflow.backend.security;

import com.aquaflow.backend.entity.User;
import com.aquaflow.backend.infrastructure.security.CustomUserDetailsService;
import com.aquaflow.backend.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    @DisplayName("ADMIN role normalizes to ROLE_ADMIN")
    void shouldNormalizeUnprefixedAdminRole() {
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("ADMIN")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("ROLE_ADMIN role remains ROLE_ADMIN without duplicate prefix")
    void shouldKeepPrefixedAdminRole() {
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("ROLE_ADMIN")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("VIEWER role normalizes to ROLE_VIEWER")
    void shouldNormalizeUnprefixedViewerRole() {
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("VIEWER")).isEqualTo("ROLE_VIEWER");
    }

    @Test
    @DisplayName("ROLE_VIEWER role remains ROLE_VIEWER")
    void shouldKeepPrefixedViewerRole() {
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("ROLE_VIEWER")).isEqualTo("ROLE_VIEWER");
    }

    @Test
    @DisplayName("OPERATOR and ROLE_OPERATOR normalize consistently")
    void shouldNormalizeOperatorRole() {
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("OPERATOR")).isEqualTo("ROLE_OPERATOR");
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("ROLE_OPERATOR")).isEqualTo("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("Null, empty, or blank role falls back to safe default ROLE_VIEWER")
    void shouldFallbackToSafeDefaultWhenRoleIsMissing() {
        assertThat(CustomUserDetailsService.normalizeRoleAuthority(null)).isEqualTo("ROLE_VIEWER");
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("")).isEqualTo("ROLE_VIEWER");
        assertThat(CustomUserDetailsService.normalizeRoleAuthority("   ")).isEqualTo("ROLE_VIEWER");
    }

    @Test
    @DisplayName("loadUserByUsername successfully constructs UserDetails with normalized authorities for ROLE_ADMIN")
    void shouldLoadUserByUsernameWithPrefixedRole() {
        User user = User.builder()
                .username("Gladwin")
                .password("encoded_pass")
                .role("ROLE_ADMIN")
                .enabled(true)
                .build();

        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("gladwin", "gladwin"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = service.loadUserByUsername("gladwin");

        assertThat(userDetails.getUsername()).isEqualTo("Gladwin");
        assertThat(userDetails.getPassword()).isEqualTo("encoded_pass");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("loadUserByUsername successfully constructs UserDetails with normalized authorities for unprefixed ADMIN")
    void shouldLoadUserByUsernameWithUnprefixedRole() {
        User user = User.builder()
                .username("Pranz")
                .password("encoded_pass")
                .role("OPERATOR")
                .enabled(true)
                .build();

        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("pranz", "pranz"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = service.loadUserByUsername("pranz");

        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("loadUserByUsername resolves alias farmer_m to MrFarmer")
    void shouldResolveFarmerMAlias() {
        User user = User.builder()
                .username("MrFarmer")
                .password("encoded_pass")
                .role("ROLE_USER")
                .enabled(true)
                .build();

        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("MrFarmer", "mr.farmer@aquaflow.io"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = service.loadUserByUsername("farmer_m");

        assertThat(userDetails.getUsername()).isEqualTo("MrFarmer");
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("loadUserByUsername resolves alias farmer_f to MsFarmer")
    void shouldResolveFarmerFAlias() {
        User user = User.builder()
                .username("MsFarmer")
                .password("encoded_pass")
                .role("ROLE_USER")
                .enabled(true)
                .build();

        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("MsFarmer", "ms.farmer@aquaflow.io"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = service.loadUserByUsername("farmer_f");

        assertThat(userDetails.getUsername()).isEqualTo("MsFarmer");
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user does not exist")
    void shouldThrowUsernameNotFoundExceptionForMissingUser() {
        when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("nonexistent", "nonexistent"))
                .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("nonexistent"));
    }
}