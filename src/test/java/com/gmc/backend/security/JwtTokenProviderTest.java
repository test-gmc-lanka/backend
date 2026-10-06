package com.gmc.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // Base64 of "testSecretKeyForUnitTestingPurposesOnly" (39 bytes = 312 bits > 256 min)
    private static final String SECRET = "dGVzdFNlY3JldEtleUZvclVuaXRUZXN0aW5nUHVycG9zZXNPbmx5";

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();
        Field secretField = JwtTokenProvider.class.getDeclaredField("secretKey");
        secretField.setAccessible(true);
        secretField.set(jwtTokenProvider, SECRET);
    }

    @Test
    void generateToken_shouldReturnNonNullToken() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "CUSTOMER");

        String token = jwtTokenProvider.generateToken(userDetails, claims);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractUsername_shouldReturnEmailFromToken() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        String token = jwtTokenProvider.generateToken(userDetails, new HashMap<>());

        String extracted = jwtTokenProvider.extractUsername(token);

        assertEquals("test@example.com", extracted);
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("user@example.com");
        String token = jwtTokenProvider.generateToken(userDetails, new HashMap<>());

        boolean valid = jwtTokenProvider.validateToken(token, userDetails);

        assertTrue(valid);
    }

    @Test
    void validateToken_shouldReturnFalseForWrongUser() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("user@example.com");
        String token = jwtTokenProvider.generateToken(userDetails, new HashMap<>());

        UserDetails otherUser = mock(UserDetails.class);
        when(otherUser.getUsername()).thenReturn("other@example.com");

        assertFalse(jwtTokenProvider.validateToken(token, otherUser));
    }

    @Test
    void extractExpiration_shouldReturnFutureDate() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("user@example.com");
        String token = jwtTokenProvider.generateToken(userDetails, new HashMap<>());

        assertNotNull(jwtTokenProvider.extractExpiration(token));
        assertTrue(jwtTokenProvider.extractExpiration(token).after(new java.util.Date()));
    }
}
