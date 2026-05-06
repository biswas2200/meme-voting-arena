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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying admin authorization on tournament approve/reject endpoints.
 *
 * Validates: Requirement 6.3
 * "IF a non-Admin user attempts to approve or reject a Tournament,
 *  THEN THE Tournament_Service SHALL return an HTTP 403 Forbidden response."
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AdminAuthorizationIntegrationTest {

    private static final String PREFIX = "AdminAuthTest_";

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
    private User adminUser;
    private String userToken;
    private String adminToken;
    private List<Meme> testMemes;
    private Long tournamentId;

    @BeforeEach
    void setUp() {
        cleanUp();

        // Create a regular USER
        regularUser = new User();
        regularUser.setUsername(PREFIX + "user_" + System.currentTimeMillis());
        regularUser.setEmail(PREFIX + "user_" + System.currentTimeMillis() + "@test.com");
        regularUser.setPassword(passwordEncoder.encode("password123"));
        regularUser.setRole(User.Role.USER);
        regularUser = userRepository.save(regularUser);

        // Create an ADMIN user (used to create the tournament so we have a valid one to test against)
        adminUser = new User();
        adminUser.setUsername(PREFIX + "admin_" + System.currentTimeMillis());
        adminUser.setEmail(PREFIX + "admin_" + System.currentTimeMillis() + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("adminpassword123"));
        adminUser.setRole(User.Role.ADMIN);
        adminUser = userRepository.save(adminUser);

        userToken = generateToken(regularUser);
        adminToken = generateToken(adminUser);

        // Create 8 memes for the tournament
        testMemes = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Meme meme = new Meme();
            meme.setTitle(PREFIX + "Meme_" + i + "_" + System.currentTimeMillis());
            meme.setImageUrl("https://example.com/" + PREFIX + "meme" + i + ".jpg");
            meme.setUploadedBy(regularUser);
            meme.setVoteCount(0);
            testMemes.add(memeRepository.save(meme));
        }

        // Create a tournament (as the regular user) so we have a PENDING_APPROVAL tournament to test against
        List<Long> memeIds = testMemes.stream().map(Meme::getId).collect(Collectors.toList());
        Map<String, Object> createRequest = Map.of(
                "name", PREFIX + "Auth Test Tournament",
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
        // Delete in dependency order: votes → matchups → tournaments → memes → users
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
    // Test 1: Non-admin user receives 403 when attempting to approve a tournament
    // =========================================================================

    /**
     * A user with ROLE_USER (not ROLE_ADMIN) must receive HTTP 403 Forbidden
     * when calling POST /api/battle/tournaments/{id}/approve.
     *
     * Validates: Requirement 6.3
     */
    @Test
    void nonAdminUser_approveTournament_returns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(userToken)),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("Non-admin user should receive 403 Forbidden when attempting to approve a tournament")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // Test 2: Non-admin user receives 403 when attempting to reject a tournament
    // =========================================================================

    /**
     * A user with ROLE_USER (not ROLE_ADMIN) must receive HTTP 403 Forbidden
     * when calling POST /api/battle/tournaments/{id}/reject.
     *
     * Validates: Requirement 6.3
     */
    @Test
    void nonAdminUser_rejectTournament_returns403() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId + "/reject",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(userToken)),
                String.class
        );

        assertThat(response.getStatusCode())
                .as("Non-admin user should receive 403 Forbidden when attempting to reject a tournament")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // Test 3 (sanity check): Admin user CAN approve a tournament (200 OK)
    // =========================================================================

    /**
     * Sanity check: a user with ROLE_ADMIN should successfully approve a tournament.
     * This confirms the 403 tests above are not caused by a broken endpoint.
     *
     * Validates: Requirement 6.1
     */
    @Test
    void adminUser_approveTournament_returns200() {
        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                TournamentResponse.class
        );

        assertThat(response.getStatusCode())
                .as("Admin user should be able to approve a tournament")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("ACTIVE");
    }

    // =========================================================================
    // Test 4 (sanity check): Admin user CAN reject a tournament (200 OK)
    // =========================================================================

    /**
     * Sanity check: a user with ROLE_ADMIN should successfully reject a tournament.
     * This confirms the 403 tests above are not caused by a broken endpoint.
     *
     * Validates: Requirement 6.2
     */
    @Test
    void adminUser_rejectTournament_returns200() {
        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId + "/reject",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                TournamentResponse.class
        );

        assertThat(response.getStatusCode())
                .as("Admin user should be able to reject a tournament")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("REJECTED");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String baseUrl() {
        return "http://localhost:" + port;
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
