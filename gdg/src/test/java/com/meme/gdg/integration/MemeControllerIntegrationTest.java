package com.meme.gdg.integration;

import com.meme.gdg.dto.MemeResponse;
import com.meme.gdg.model.Meme;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MemeController — gallery, leaderboard, create/vote/delete,
 * and the quick-battle meme-pair endpoint.
 *
 * Uses the "prod" Spring profile (strict JWT enforcement) with an H2 in-memory
 * database standing in for PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:meme_arena_meme_controller_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.cors.allowed-origins=*",
        "app.jwt.secret=test_secret_key_for_meme_controller_tests_only_not_for_production",
        "app.jwt.expiration-ms=86400000"
})
class MemeControllerIntegrationTest {

    private static final String PREFIX = "MemeCtrlTest_";

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

    private User owner;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        cleanUp();
        owner = createUser(PREFIX + "owner", PREFIX + "owner@test.com");
        ownerToken = generateToken(owner);
    }

    @AfterEach
    void cleanUp() {
        voteRepository.findAll().stream()
                .filter(v -> v.getUser() != null && v.getUser().getUsername() != null
                        && v.getUser().getUsername().startsWith(PREFIX))
                .forEach(voteRepository::delete);
        memeRepository.findAll().stream()
                .filter(m -> m.getTitle() != null && m.getTitle().startsWith(PREFIX))
                .forEach(memeRepository::delete);
        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith(PREFIX))
                .forEach(userRepository::delete);
    }

    // =========================================================================
    // GET /api/memes , GET /api/memes/leaderboard — public
    // =========================================================================

    @Test
    void getAllMemes_publicAccess_returns200() {
        createMemeDirect(PREFIX + "Gallery Meme", 5);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/memes?page=0&size=12", HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    void getLeaderboard_publicAccess_returns200() {
        createMemeDirect(PREFIX + "Top Meme", 100);

        ResponseEntity<MemeResponse[]> response = restTemplate.exchange(
                baseUrl() + "/api/memes/leaderboard", HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()), MemeResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()[0].getTitle()).isEqualTo(PREFIX + "Top Meme");
    }

    // =========================================================================
    // POST /api/memes — create
    // =========================================================================

    @Test
    void createMeme_authenticated_returns200AndSavedMeme() {
        Map<String, Object> body = Map.of(
                "title", PREFIX + "Created Meme",
                "imageUrl", "https://example.com/created.jpg"
        );

        ResponseEntity<MemeResponse> response = restTemplate.exchange(
                baseUrl() + "/api/memes", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(ownerToken)), MemeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo(PREFIX + "Created Meme");
        assertThat(response.getBody().getVoteCount()).isEqualTo(0);
        assertThat(response.getBody().getUploadedBy()).isEqualTo(PREFIX + "owner");
    }

    @Test
    void createMeme_unauthenticated_returns401() {
        Map<String, Object> body = Map.of(
                "title", PREFIX + "Should Fail",
                "imageUrl", "https://example.com/fail.jpg"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/memes", HttpMethod.POST,
                new HttpEntity<>(body, noAuthHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createMeme_blankTitle_returns400ValidationError() {
        Map<String, Object> body = Map.of(
                "title", "",
                "imageUrl", "https://example.com/blank.jpg"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/memes", HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(ownerToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("title");
    }

    // =========================================================================
    // PUT /api/memes/{id}/vote
    // =========================================================================

    @Test
    void voteMeme_upvoteAuthenticated_returns200AndIncrementedCount() {
        Meme meme = createMemeDirect(PREFIX + "Votable Meme", 3);

        Map<String, Object> body = Map.of("voteType", "UPVOTE");

        ResponseEntity<MemeResponse> response = restTemplate.exchange(
                baseUrl() + "/api/memes/" + meme.getId() + "/vote", HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(ownerToken)), MemeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getVoteCount()).isEqualTo(4);
    }

    @Test
    void voteMeme_unauthenticated_returns401() {
        Meme meme = createMemeDirect(PREFIX + "Vote Guard Meme", 0);

        Map<String, Object> body = Map.of("voteType", "UPVOTE");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/memes/" + meme.getId() + "/vote", HttpMethod.PUT,
                new HttpEntity<>(body, noAuthHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void voteMeme_nonexistentMeme_returns400() {
        Map<String, Object> body = Map.of("voteType", "UPVOTE");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/memes/999999/vote", HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders(ownerToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("Meme not found");
    }

    // =========================================================================
    // DELETE /api/memes/{id}
    // =========================================================================

    @Test
    void deleteMeme_owner_returns200AndRemovesMeme() {
        Meme meme = createMemeDirect(PREFIX + "Deletable Meme", 0);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/memes/" + meme.getId(), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(ownerToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memeRepository.findById(meme.getId())).isEmpty();
    }

    @Test
    void deleteMeme_nonOwnerNonAdmin_returns400() {
        Meme meme = createMemeDirect(PREFIX + "Protected Meme", 0);

        User intruder = createUser(PREFIX + "intruder", PREFIX + "intruder@test.com");
        String intruderToken = generateToken(intruder);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/memes/" + meme.getId(), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(intruderToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(memeRepository.findById(meme.getId())).isPresent();
    }

    @Test
    void deleteMeme_unauthenticated_returns401() {
        Meme meme = createMemeDirect(PREFIX + "Guarded Meme", 0);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/memes/" + meme.getId(), HttpMethod.DELETE,
                new HttpEntity<>(noAuthHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // GET /api/memes/battle — public
    // =========================================================================

    @Test
    void getBattleMemes_withEnoughMemes_returnsTwoDistinctMemes() {
        createMemeDirect(PREFIX + "Battle Meme A", 0);
        createMemeDirect(PREFIX + "Battle Meme B", 0);

        ResponseEntity<Meme[]> response = restTemplate.exchange(
                baseUrl() + "/api/memes/battle", HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()), Meme[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].getId()).isNotEqualTo(response.getBody()[1].getId());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole(User.Role.USER);
        return userRepository.save(user);
    }

    private Meme createMemeDirect(String title, int voteCount) {
        Meme meme = new Meme();
        meme.setTitle(title);
        meme.setImageUrl("https://example.com/" + title.hashCode() + ".jpg");
        meme.setUploadedBy(owner);
        meme.setVoteCount(voteCount);
        return memeRepository.save(meme);
    }

    private String generateToken(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        return jwtUtils.generateJwtToken(auth);
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
