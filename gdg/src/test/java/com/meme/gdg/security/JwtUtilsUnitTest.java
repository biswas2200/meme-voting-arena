package com.meme.gdg.security;

import com.meme.gdg.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwtUtils — token generation, parsing, and validation.
 * JwtUtils is a plain component (no Spring context needed); @Value fields
 * are injected manually via ReflectionTestUtils.
 */
class JwtUtilsUnitTest {

    private static final String SECRET = "unit_test_secret_key_at_least_32_characters_long_for_hs256";
    private static final long EXPIRATION_MS = 86_400_000L; // 24h

    private JwtUtils jwtUtils;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", EXPIRATION_MS);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("jwtuser");
        testUser.setEmail("jwtuser@test.com");
        testUser.setPassword("encoded");
        testUser.setRole(User.Role.USER);
    }

    private UsernamePasswordAuthenticationToken authFor(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void generateJwtToken_returnsNonBlankToken() {
        String token = jwtUtils.generateJwtToken(authFor(testUser));

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    void generateJwtToken_thenGetUserName_roundTripsUsername() {
        String token = jwtUtils.generateJwtToken(authFor(testUser));

        String username = jwtUtils.getUserNameFromJwtToken(token);

        assertThat(username).isEqualTo("jwtuser");
    }

    @Test
    void validateJwtToken_validToken_returnsTrue() {
        String token = jwtUtils.generateJwtToken(authFor(testUser));

        assertThat(jwtUtils.validateJwtToken(token)).isTrue();
    }

    @Test
    void validateJwtToken_malformedToken_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("this.is.not-a-valid-jwt")).isFalse();
    }

    @Test
    void validateJwtToken_emptyString_returnsFalse() {
        assertThat(jwtUtils.validateJwtToken("")).isFalse();
    }

    @Test
    void validateJwtToken_signedWithDifferentSecret_returnsFalse() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a_completely_different_secret_key_that_is_also_32_bytes".getBytes());
        String foreignToken = Jwts.builder()
                .subject("jwtuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(otherKey)
                .compact();

        assertThat(jwtUtils.validateJwtToken(foreignToken)).isFalse();
    }

    @Test
    void validateJwtToken_expiredToken_returnsFalse() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .subject("jwtuser")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000)) // expired 5s ago
                .signWith(key)
                .compact();

        assertThat(jwtUtils.validateJwtToken(expiredToken)).isFalse();
    }
}
