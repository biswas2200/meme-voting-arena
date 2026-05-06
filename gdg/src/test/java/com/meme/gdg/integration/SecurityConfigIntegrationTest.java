package com.meme.gdg.integration;

import com.meme.gdg.model.User;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.security.JwtUtils;
import com.meme.gdg.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying the SecurityConfig rules for the dev profile.
 *
 * The dev profile uses a permissive filter chain (all requests pass through),
 * but the JWT filter still runs — so authenticated endpoints return 400 when
 * the user lookup fails (no token → anonymous principal → service throws).
 *
 * Key dev-profile behaviors documented here:
 * - Public endpoints → 200 (no token required)
 * - Write endpoints with no token → 400 (dev chain permits the request but
 *   the service layer throws "Authentication required", caught by the controller
 *   which returns 400 Bad Request)
 * - Authenticated endpoints (profile) with no token → 400 (same reason)
 * - Admin endpoints with no token → 500 in dev (no 403 because the dev chain
 *   does not enforce roles; the @PreAuthorize annotation fires but without a
 *   principal the method security throws AccessDeniedException → 500 in dev)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "app.jwt.secret=test_secret_key_for_security_config_integration_tests_only",
        "app.jwt.expiration-ms=86400000"
})
class SecurityConfigIntegrationTest {

    private static final String PREFIX = "SecCfgTest_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemeRepository memeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private User testUser;
    private User adminUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        cleanUp();

        // Seed a regular user
        testUser = new User();
        testUser.setUsername(PREFIX + "user_" + System.currentTimeMillis());
        testUser.setEmail(PREFIX + "user_" + System.currentTimeMillis() + "@test.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setRole(User.Role.USER);
        testUser = userRepository.save(testUser);
        userToken = generateToken(testUser);

        // Seed an admin user
        adminUser = new User();
        adminUser.setUsername(PREFIX + "admin_" + System.currentTimeMillis());
        adminUser.setEmail(PREFIX + "admin_" + System.currentTimeMillis() + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("admin123"));
        adminUser.setRole(User.Role.ADMIN);
        adminUser = userRepository.save(adminUser);
        adminToken = generateToken(adminUser);
    }

    @AfterEach
    void cleanUp() {
        memeRepository.findAll().stream()
                .filter(m -> m.getTitle() != null && m.getTitle().startsWith(PREFIX))
                .forEach(memeRepository::delete);

        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith(PREFIX))
                .forEach(userRepository::delete);
    }

    // =========================================================================
    // 1. Public endpoints — always 200
    // =========================================================================

    /**
     * GET /api/memes is public — returns 200 without any token.
     */
    @Test
    void getMemesPublic_noToken_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/memes",
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("GET /api/memes should be publicly accessible (200 OK)")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * GET /api/memes/leaderboard is public — returns 200 without any token.
     */
    @Test
    void getLeaderboardPublic_noToken_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/memes/leaderboard",
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("GET /api/memes/leaderboard should be publicly accessible (200 OK)")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * GET /api/battle/tournaments is public — returns 200 without any token.
     */
    @Test
    void getTournamentsPublic_noToken_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments",
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("GET /api/battle/tournaments should be publicly accessible (200 OK)")
                .isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // 2. Write endpoints with no token — non-200 in dev profile
    //
    // In dev, the security chain permits all requests, but the service layer
    // throws RuntimeException("Authentication required") when authentication
    // is null. The controller catches this and returns 400 Bad Request.
    // =========================================================================

    /**
     * POST /api/memes with no token → non-200.
     *
     * Dev profile behavior: the request reaches the controller, the service
     * throws "Authentication required", the controller returns 400.
     */
    @Test
    void createMeme_noToken_returnsNon200() {
        Map<String, String> body = Map.of(
                "title", PREFIX + "Test Meme",
                "imageUrl", "https://example.com/test.jpg"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/memes",
                HttpMethod.POST,
                new HttpEntity<>(body, noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode().value())
                .as("POST /api/memes with no token should return non-200 in dev profile")
                .isNotEqualTo(200);
    }

    // =========================================================================
    // 3. Auth endpoints — signin and signup return 200
    // =========================================================================

    /**
     * POST /api/auth/signin with valid credentials → 200.
     */
    @Test
    void signin_withValidCredentials_returns200() {
        Map<String, String> body = Map.of(
                "username", testUser.getUsername(),
                "password", "password123"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/signin",
                HttpMethod.POST,
                new HttpEntity<>(body, noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("POST /api/auth/signin with valid credentials should return 200 OK")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("token");
    }

    /**
     * POST /api/auth/signup with valid data → 200.
     */
    @Test
    void signup_withValidData_returns200() {
        // Username must be 3–20 chars (SignupRequest @Size constraint).
        // Use last 5 digits of timestamp to stay well within the limit.
        String shortSuffix = String.valueOf(System.currentTimeMillis() % 100000);
        String newUsername = "sc_nu_" + shortSuffix;   // e.g. "sc_nu_12345" — 11 chars
        String newEmail    = "sc_nu_" + shortSuffix + "@test.com";

        Map<String, String> body = Map.of(
                "username", newUsername,
                "email",    newEmail,
                "password", "password123"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(body, noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("POST /api/auth/signup with valid data should return 200 OK")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("token");

        // Clean up the newly created user
        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().equals(newUsername))
                .forEach(userRepository::delete);
    }

    // =========================================================================
    // 4. Profile endpoint — non-200 without token in dev profile
    //
    // In dev, the request reaches the controller. AuthController checks
    // authentication == null and returns 400 Bad Request.
    // =========================================================================

    /**
     * GET /api/auth/profile with no token → 400 in dev profile.
     *
     * The AuthController explicitly checks for null authentication and returns
     * 400 Bad Request (not 401, because the dev chain does not enforce auth).
     */
    @Test
    void getProfile_noToken_returns400InDev() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile",
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("GET /api/auth/profile with no token should return 400 in dev profile")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * GET /api/auth/profile with a valid token → 200.
     */
    @Test
    void getProfile_withValidToken_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken)),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("GET /api/auth/profile with valid token should return 200 OK")
                .isEqualTo(HttpStatus.OK);
    }

    // =========================================================================
    // 5. Admin endpoint — non-200 without token
    //
    // POST /api/battle/tournaments/{id}/approve has @PreAuthorize("hasRole('ADMIN')").
    // In dev, the security chain permits the request, but method security fires.
    // Without a principal, Spring Security throws AccessDeniedException → 500 in dev.
    // =========================================================================

    /**
     * POST /api/battle/tournaments/{id}/approve with no token → non-200.
     *
     * In dev profile: @PreAuthorize fires, AccessDeniedException is thrown → 500.
     * In prod profile: the security chain returns 401 before reaching the controller.
     */
    @Test
    void approveTournament_noToken_returnsNon200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/999/approve",
                HttpMethod.POST,
                new HttpEntity<>(noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode().value())
                .as("POST /api/battle/tournaments/{id}/approve with no token should return non-200")
                .isNotEqualTo(200);
    }

    /**
     * POST /api/battle/tournaments/{id}/approve with USER token (not ADMIN) → non-200.
     */
    @Test
    void approveTournament_withUserToken_returnsNon200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/999/approve",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(userToken)),
                String.class
        );

        assertThat(response.getStatusCode().value())
                .as("POST /api/battle/tournaments/{id}/approve with USER token should return non-200 (403)")
                .isNotEqualTo(200);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders noAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String generateToken(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtUtils.generateJwtToken(auth);
    }
}
