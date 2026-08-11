package com.meme.gdg.integration;

import com.meme.gdg.dto.UserStatsResponse;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.repository.VoteRepository;
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
 * Integration tests for AuthController — signup, signin, and profile endpoints.
 *
 * Uses the "prod" Spring profile to exercise the strict JWT security filter chain
 * (the same chain that runs in the deployed environment), overriding the datasource
 * with an in-memory H2 instance so no external PostgreSQL is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:meme_arena_auth_controller_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.cors.allowed-origins=*",
        "app.jwt.secret=test_secret_key_for_auth_controller_tests_only_not_for_production",
        "app.jwt.expiration-ms=86400000"
})
class AuthControllerIntegrationTest {

    private static final String PREFIX = "AuthCT_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemeRepository memeRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        voteRepository.findAll().stream()
                .filter(v -> v.getUser() != null && v.getUser().getUsername() != null
                        && v.getUser().getUsername().startsWith(PREFIX))
                .forEach(voteRepository::delete);
        memeRepository.findAll().stream()
                .filter(m -> m.getUploadedBy() != null && m.getUploadedBy().getUsername() != null
                        && m.getUploadedBy().getUsername().startsWith(PREFIX))
                .forEach(memeRepository::delete);
        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith(PREFIX))
                .forEach(userRepository::delete);
    }

    // =========================================================================
    // POST /api/auth/signup
    // =========================================================================

    @Test
    void signup_withValidData_returns200AndTokenAndUser() {
        Map<String, Object> body = Map.of(
                "username", PREFIX + "newuser",
                "email", PREFIX + "newuser@test.com",
                "password", "password123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        Map<?, ?> user = (Map<?, ?>) response.getBody().get("user");
        assertThat(user.get("username")).isEqualTo(PREFIX + "newuser");
        assertThat(user.get("email")).isEqualTo(PREFIX + "newuser@test.com");
        assertThat(user.get("role")).isEqualTo("USER");

        assertThat(userRepository.existsByUsername(PREFIX + "newuser")).isTrue();
    }

    @Test
    void signup_duplicateUsername_returns400() {
        createUser(PREFIX + "taken", PREFIX + "first@test.com", "password123");

        Map<String, Object> body = Map.of(
                "username", PREFIX + "taken",
                "email", PREFIX + "second@test.com",
                "password", "password123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("Username is already taken");
    }

    @Test
    void signup_duplicateEmail_returns400() {
        createUser(PREFIX + "first", PREFIX + "dupe@test.com", "password123");

        Map<String, Object> body = Map.of(
                "username", PREFIX + "second",
                "email", PREFIX + "dupe@test.com",
                "password", "password123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("Email is already in use");
    }

    @Test
    void signup_invalidEmail_returns400ValidationError() {
        Map<String, Object> body = Map.of(
                "username", PREFIX + "bademail",
                "email", "not-an-email",
                "password", "password123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("email");
    }

    @Test
    void signup_passwordTooShort_returns400ValidationError() {
        Map<String, Object> body = Map.of(
                "username", PREFIX + "shortpw",
                "email", PREFIX + "shortpw@test.com",
                "password", "abc"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("password");
    }

    // =========================================================================
    // POST /api/auth/signin
    // =========================================================================

    @Test
    void signin_withValidCredentials_returns200AndToken() {
        createUser(PREFIX + "signinuser", PREFIX + "signin@test.com", "correctPassword");

        Map<String, Object> body = Map.of(
                "username", PREFIX + "signinuser",
                "password", "correctPassword"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signin", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat((String) response.getBody().get("token")).isNotBlank();
    }

    @Test
    void signin_withWrongPassword_returns400() {
        createUser(PREFIX + "wrongpw", PREFIX + "wrongpw@test.com", "correctPassword");

        Map<String, Object> body = Map.of(
                "username", PREFIX + "wrongpw",
                "password", "wrongPassword"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signin", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("Invalid username or password");
    }

    @Test
    void signin_nonexistentUser_returns400() {
        Map<String, Object> body = Map.of(
                "username", PREFIX + "ghost",
                "password", "whatever123"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signin", jsonEntity(body), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // GET /api/auth/profile
    // =========================================================================

    @Test
    void profile_withValidToken_returns200AndStats() {
        User user = createUser(PREFIX + "profileuser", PREFIX + "profile@test.com", "password123");
        String token = generateToken(user);

        ResponseEntity<UserStatsResponse> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), UserStatsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsername()).isEqualTo(PREFIX + "profileuser");
        assertThat(response.getBody().getTotalMemesUploaded()).isEqualTo(0);
    }

    @Test
    void profile_withoutToken_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void profile_withMalformedToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer not-a-real-jwt-token");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // PUT /api/auth/profile
    // =========================================================================

    @Test
    void updateProfile_changeUsername_returns200() {
        User user = createUser(PREFIX + "renameme", PREFIX + "rename@test.com", "password123");
        String token = generateToken(user);

        Map<String, Object> body = Map.of("username", PREFIX + "renamed");

        ResponseEntity<UserStatsResponse> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), UserStatsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo(PREFIX + "renamed");
    }

    @Test
    void updateProfile_duplicateUsername_returns400() {
        createUser(PREFIX + "existing", PREFIX + "existing@test.com", "password123");
        User user = createUser(PREFIX + "wantsrename", PREFIX + "wantsrename@test.com", "password123");
        String token = generateToken(user);

        Map<String, Object> body = Map.of("username", PREFIX + "existing");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("already taken");
    }

    @Test
    void updateProfile_wrongCurrentPassword_returns400() {
        User user = createUser(PREFIX + "pwchange", PREFIX + "pwchange@test.com", "password123");
        String token = generateToken(user);

        Map<String, Object> body = Map.of(
                "currentPassword", "notTheRealPassword",
                "newPassword", "newPassword456"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("Current password is incorrect");
    }

    @Test
    void updateProfile_withoutToken_returns401() {
        Map<String, Object> body = Map.of("username", PREFIX + "shouldfail");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/auth/profile", HttpMethod.PUT,
                new HttpEntity<>(body, noAuthHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private User createUser(String username, String email, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private String generateToken(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtUtils.generateJwtToken(auth);
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
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
}
