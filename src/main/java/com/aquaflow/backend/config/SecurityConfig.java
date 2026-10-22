package com.aquaflow.backend.config;

import com.aquaflow.backend.infrastructure.security.CustomAccessDeniedHandler;
import com.aquaflow.backend.infrastructure.security.CustomAuthenticationEntryPoint;
import com.aquaflow.backend.infrastructure.security.GatewayAuthenticationFilter;
import com.aquaflow.backend.infrastructure.security.JwtAuthenticationFilter;
import com.aquaflow.backend.infrastructure.security.RateLimitingFilter;
import com.aquaflow.backend.infrastructure.security.SecurityHeadersFilter;
import com.aquaflow.backend.infrastructure.security.WebhookAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final WebhookAuthenticationFilter webhookAuthenticationFilter;
    private final SecurityHeadersFilter securityHeadersFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final UserDetailsService userDetailsService;
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;
    private final com.aquaflow.backend.infrastructure.security.DeviceAuthenticationFilter deviceAuthenticationFilter;

    @Value("${aquaflow.cors.allowed-origins:*}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          WebhookAuthenticationFilter webhookAuthenticationFilter,
                          SecurityHeadersFilter securityHeadersFilter,
                          RateLimitingFilter rateLimitingFilter,
                          CustomAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler,
                          UserDetailsService userDetailsService,
                          GatewayAuthenticationFilter gatewayAuthenticationFilter,
                          com.aquaflow.backend.infrastructure.security.DeviceAuthenticationFilter deviceAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.webhookAuthenticationFilter = webhookAuthenticationFilter;
        this.securityHeadersFilter = securityHeadersFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.userDetailsService = userDetailsService;
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
        this.deviceAuthenticationFilter = deviceAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/auth/register").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pairing/requests", "/api/v1/pairing/request").permitAll()
                        .requestMatchers("/api/v1/pairing/devices/**", "/api/v1/pairing/status/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pairing/requests/*/provision", "/api/v1/pairing/requests/*/complete").permitAll()
                        .requestMatchers("/api/v1/pairing/requests/pending", "/api/v1/pairing/requests").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/pairing/requests/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/pairing/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/telemetry/uplink/**", "/api/v1/telemetry/uplink", "/api/v1/telemetry/batch-uplink").permitAll()
                        .requestMatchers("/api/v1/telemetry/stream/**").hasAnyRole("ADMIN", "OPERATOR", "SUPERVISOR")
                        .requestMatchers("/api/v1/telemetry/stream").permitAll()
                        .requestMatchers("/api/v1/system/master-state").hasAnyRole("ADMIN", "OPERATOR", "SUPERVISOR")
                        .requestMatchers("/api/v1/system/health").hasAnyRole("ADMIN", "OPERATOR", "SUPERVISOR")
                        .requestMatchers("/api/v1/chirpstack/webhook/**", "/api/v1/lora/webhook/**").hasAnyRole("EDGE_NODE", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/audit/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/irrigation/pending-command/**", "/api/v1/irrigation/ack/**").hasAnyRole("GATEWAY", "ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/irrigation/emergency-stop", "/api/v1/irrigation/manual/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/nodes/*/pairing", "/api/v1/nodes/*/unpair").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/nodes/*/unpair").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/v1/nodes/*/commission", "/api/v1/nodes/*/decommission", "/api/v1/nodes/*/replace").hasRole("ADMIN")
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(webhookAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(deviceAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (origins.contains("*") || origins.isEmpty()) {
            configuration.addAllowedOriginPattern("*");
        } else {
            configuration.setAllowedOriginPatterns(origins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Client",
                "X-Correlation-ID",
                "X-Webhook-Secret",
                "X-Downlink-Secret",
                "X-ArvaCore-Gateway-Key",
                "X-Arva-Node-ID",
                "X-Arva-Key-ID",
                "X-Arva-Timestamp",
                "X-Arva-Nonce",
                "X-Arva-Signature",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-ID", "Authorization", "X-Arva-Node-ID", "X-Arva-Key-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
