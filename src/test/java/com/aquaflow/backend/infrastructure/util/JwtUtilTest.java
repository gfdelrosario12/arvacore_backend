package com.aquaflow.backend.infrastructure.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "defaultSecretKeyThatShouldBeAtLeast256BitsLongForHS256Algorithm");
    }

    @Test
    void testStandardExpiringToken() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L); // 1 hour
        String token = jwtUtil.generateToken("admin");

        assertNotNull(token);
        assertEquals("admin", jwtUtil.extractUsername(token));
        assertFalse(jwtUtil.isTokenExpired(token));
        assertNotNull(jwtUtil.extractExpiration(token));

        UserDetails userDetails = new User("admin", "password", Collections.emptyList());
        assertTrue(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void testUnlimitedNoExpiryToken() {
        // When expirationMs <= 0, token has no exp claim (never expires)
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 0L);
        String token = jwtUtil.generateToken("admin");

        assertNotNull(token);
        assertEquals("admin", jwtUtil.extractUsername(token));
        assertNull(jwtUtil.extractExpiration(token));
        assertFalse(jwtUtil.isTokenExpired(token));

        UserDetails userDetails = new User("admin", "password", Collections.emptyList());
        assertTrue(jwtUtil.validateToken(token, userDetails));
    }
}

