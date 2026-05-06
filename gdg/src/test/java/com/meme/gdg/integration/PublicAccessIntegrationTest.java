package com.meme.gdg.integration;

import com.meme.gdg.dto.TournamentResponse;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.Tournament;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.BattleVoteRepository;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying public access rules for the Battle Arena.
 *
 * Uses the "prod" Spring profile to activate the strict security filter chain
 * (which enforces JWT authentication), while overriding the datasource to use
 * an H2 in-memory database so no external PostgreSQL instance is required.
 *
 * Validates:
 * - Requirement 10.3: Results page accessible to unauthenticated users
 * - Requirement 2.5: Only authenticated users may submit votes (401 for unauthenticated)
 * - Requirement 5.6: Tournament creation requires authentication (401 for unauthenticated)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        // Override PostgreSQL datasource with H2 for tests
        "spring.datasource.url=jdbc:h2:mem:meme_arena_public_access_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        // Override prod env-var-required properties
        "app.cors.allowed-origins=*",
        "app.jwt.secret=test_secret_key_for_public_access_tests_only_not_for_production",
        "app.jwt.expiration-ms=86400000"
})
class PublicAccessIntegrationTest {

    private static final String PREFIX = "PublicAccessTest_";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private TournamentMatchupRepository tournamentMatchupRepository;

    @Autowired
    private BattleVoteRepository battleVoteRepository;

    @Autowired
    private MemeRepository memeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private User regularUser;
    private String userToken;
    private List<Meme> testMemes;
    private Long tournamentId;

    @BeforeEach
    void setUp() {
        cleanUp();

        // Create a regular USER to seed data
        regularUser = new User();
        regularUser.setUsername(PREFIX + "user_" + System.currentTimeMillis());
        regularUser.setEmail(PREFIX + "user_" + System.currentTimeMillis() + "@test.com");
        regularUser.setPassword(passwordEncoder.encode("password123"));
        regularUser.setRole(User.Role.USER);
        regularUser = userRepository.save(regularUser);

        userToken = generateToken(regularUser);

        // Create 8 memes
        testMemes = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Meme meme = new Meme();
            meme.setTitle(PREFIX + "Meme_" + i + "_" + System.currentTimeMillis());
            meme.setImageUrl("https://example.com/" + PREFIX + "meme" + i + ".jpg");
            meme.setUploadedBy(regularUser);
            meme.setVoteCount(0);
            testMemes.add(memeRepository.save(meme));
        }

        // Create a tournament (authenticated) so we have one to fetch publicly
        List<Long> memeIds = testMemes.stream().map(Meme::getId).collect(Collectors.toList());
        Map<String, Object> createRequest = Map.of(
                "name", PREFIX + "Public Access Tournament",
                "memeIds", memeIds,
                "roundDurationHours", 1
        );

        ResponseEntity<TournamentResponse> createResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, authHeaders(userToken)),
                TournamentResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        tournamentId = createResponse.getBody().getId();
    }

    @AfterEach
    void cleanUp() {
        List<Tournament> testTournaments = tournamentRepository.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(PREFIX))
                .collect(Collectors.toList());

        for (Tournament tournament : testTournaments) {
            List<TournamentMatchup> matchups =
                    tournamentMatchupRepository.findByTournamentId(tournament.getId());
            for (TournamentMatchup matchup : matchups) {
                battleVoteRepository.findByMatchupId(matchup.getId())
                        .forEach(battleVoteRepository::delete);
            }
            tournamentMatchupRepository.deleteAll(matchups);
            tournamentRepository.delete(tournament);
        }

        memeRepository.findAll().stream()
                .filter(m -> m.getTitle() != null && m.getTitle().startsWith(PREFIX))
                .forEach(memeRepository::delete);

        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith(PREFIX))
                .forEach(userRepository::delete);
    }

    // =========================================================================
    // Public read access — unauthenticated users CAN view
    // =========================================================================

    /**
     * Unauthenticated user can list all tournaments (GET /api/battle/tournaments).
     *
     * Validates: Requirement 10.3 (public access to tournament list)
     */
    @Test
    void unauthenticatedUser_listTournaments_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments",
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("Unauthenticated user should be able to list tournaments (200 OK)")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * Unauthenticated user can view a specific tournament bracket
     * (GET /api/battle/tournaments/{id}).
     *
     * Validates: Requirement 10.3 (public access to tournament bracket)
     */
    @Test
    void unauthenticatedUser_getTournamentById_returns200() {
        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId,
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                TournamentResponse.class
        );

        assertThat(response.getStatusCode())
                .as("Unauthenticated user should be able to view a tournament bracket (200 OK)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(tournamentId);
    }

    /**
     * Unauthenticated user can view a tournament results page
     * (GET /api/battle/tournaments/{id} — same endpoint, publicly accessible).
     *
     * Validates: Requirement 10.3 (results page accessible without authentication)
     */
    @Test
    void unauthenticatedUser_getTournamentResults_returns200() {
        // The results page fetches the same endpoint as the bracket view.
        // This test confirms the endpoint is accessible without a JWT token.
        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId,
                HttpMethod.GET,
                new HttpEntity<>(noAuthHeaders()),
                TournamentResponse.class
        );

        assertThat(response.getStatusCode())
                .as("Unauthenticated user should be able to view tournament results (200 OK)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    // =========================================================================
    // Protected write access — unauthenticated users CANNOT vote or create
    // =========================================================================

    /**
     * Unauthenticated user cannot cast a Quick Battle vote
     * (POST /api/battle/vote/quick → 401 Unauthorized).
     *
     * Validates: Requirement 2.5 (only authenticated users may submit votes)
     */
    @Test
    void unauthenticatedUser_voteOnQuickBattle_returns401() {
        Map<String, Object> votePayload = Map.of(
                "battlePairId", 1L,
                "chosenMemeId", testMemes.get(0).getId()
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/vote/quick",
                HttpMethod.POST,
                new HttpEntity<>(votePayload, noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("Unauthenticated user should receive 401 when attempting to vote on a Quick Battle")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Unauthenticated user cannot cast a tournament matchup vote
     * (POST /api/battle/vote/tournament → 401 Unauthorized).
     *
     * Validates: Requirement 2.5 (only authenticated users may submit votes)
     */
    @Test
    void unauthenticatedUser_voteOnTournamentMatchup_returns401() {
        Map<String, Object> votePayload = Map.of(
                "matchupId", 1L,
                "chosenMemeId", testMemes.get(0).getId()
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/vote/tournament",
                HttpMethod.POST,
                new HttpEntity<>(votePayload, noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("Unauthenticated user should receive 401 when attempting to vote on a tournament matchup")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Unauthenticated user cannot create a tournament
     * (POST /api/battle/tournaments → 401 Unauthorized).
     *
     * Validates: Requirement 5.6 (tournament creation requires authentication)
     */
    @Test
    void unauthenticatedUser_createTournament_returns401() {
        List<Long> memeIds = testMemes.stream().map(Meme::getId).collect(Collectors.toList());
        Map<String, Object> createRequest = Map.of(
                "name", PREFIX + "Unauthorized Tournament",
                "memeIds", memeIds,
                "roundDurationHours", 1
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, noAuthHeaders()),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("Unauthenticated user should receive 401 when attempting to create a tournament")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Headers with no Authorization token — simulates an unauthenticated request. */
    private HttpHeaders noAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Headers with a valid Bearer token — simulates an authenticated request. */
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
