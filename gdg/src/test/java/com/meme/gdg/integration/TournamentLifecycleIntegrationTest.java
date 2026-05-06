package com.meme.gdg.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.dto.MatchupResponse;
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
import com.meme.gdg.scheduler.RoundAdvancementScheduler;
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
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test covering the full tournament lifecycle:
 * User creates tournament → admin approves → users vote on matchups →
 * scheduler advances round → tournament completes → results page displays champion.
 *
 * Validates: Requirements 5.1, 6.3, 7.1–7.6, 8.1, 9.1, 10.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class TournamentLifecycleIntegrationTest {

    private static final String PREFIX = "TournamentLifecycle_";

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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoundAdvancementScheduler roundAdvancementScheduler;

    private User regularUser;
    private User adminUser;
    private String userToken;
    private String adminToken;
    private List<Meme> testMemes;

    @BeforeEach
    void setUp() {
        cleanUp();

        // Create regular USER
        regularUser = new User();
        regularUser.setUsername(PREFIX + "user_" + System.currentTimeMillis());
        regularUser.setEmail(PREFIX + "user_" + System.currentTimeMillis() + "@test.com");
        regularUser.setPassword(passwordEncoder.encode("password123"));
        regularUser.setRole(User.Role.USER);
        regularUser = userRepository.save(regularUser);

        // Create ADMIN user
        adminUser = new User();
        adminUser.setUsername(PREFIX + "admin_" + System.currentTimeMillis());
        adminUser.setEmail(PREFIX + "admin_" + System.currentTimeMillis() + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("adminpassword123"));
        adminUser.setRole(User.Role.ADMIN);
        adminUser = userRepository.save(adminUser);

        // Generate JWT tokens
        userToken = generateToken(regularUser);
        adminToken = generateToken(adminUser);

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
    }

    @AfterEach
    void cleanUp() {
        // Find all test tournaments by name prefix
        List<Tournament> testTournaments = tournamentRepository.findAll().stream()
                .filter(t -> t.getName() != null && t.getName().startsWith(PREFIX))
                .collect(Collectors.toList());

        for (Tournament tournament : testTournaments) {
            Long tournamentId = tournament.getId();

            // Delete battle votes for each matchup in this tournament
            List<TournamentMatchup> matchups =
                    tournamentMatchupRepository.findByTournamentId(tournamentId);
            for (TournamentMatchup matchup : matchups) {
                battleVoteRepository.findByMatchupId(matchup.getId())
                        .forEach(battleVoteRepository::delete);
            }

            // Delete matchups
            tournamentMatchupRepository.deleteAll(matchups);

            // Delete tournament
            tournamentRepository.delete(tournament);
        }

        // Delete test memes
        memeRepository.findAll().stream()
                .filter(m -> m.getTitle() != null && m.getTitle().startsWith(PREFIX))
                .forEach(memeRepository::delete);

        // Delete test users
        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith(PREFIX))
                .forEach(userRepository::delete);
    }

    // =========================================================================
    // Test 1: Full tournament lifecycle
    // =========================================================================

    /**
     * Full tournament lifecycle:
     * Step 1: User creates tournament (PENDING_APPROVAL, 4 matchups)
     * Step 2: Admin approves (ACTIVE, currentRound=1, currentRoundEndsAt set)
     * Step 3: Vote on all 4 round-1 matchups + verify WebSocket + duplicate vote → 409
     * Step 4: Advance round 1 → round 2 (2 matchups)
     * Step 5: Vote on round-2 matchups, advance to round 3 (1 final matchup)
     * Step 6: Vote on final matchup, advance → COMPLETED, champion set
     * Step 7: GET tournament → 200 OK, status=COMPLETED, champion not null
     *
     * Validates: Requirements 5.1, 6.3, 7.1–7.6, 8.1, 9.1, 10.1
     */
    @Test
    void fullTournamentLifecycle_createApproveVoteAdvanceComplete() throws Exception {

        // ── Step 1: User creates tournament ───────────────────────────────────
        List<Long> memeIds = testMemes.stream().map(Meme::getId).collect(Collectors.toList());

        Map<String, Object> createRequest = Map.of(
                "name", PREFIX + "Test Tournament",
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
        TournamentResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(created.getMatchups()).hasSize(4); // 8 memes → 4 first-round matchups
        assertThat(created.getCurrentRound()).isNull(); // not active yet

        Long tournamentId = created.getId();

        // ── Step 2: Admin approves tournament ─────────────────────────────────
        ResponseEntity<TournamentResponse> approveResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                TournamentResponse.class
        );

        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TournamentResponse approved = approveResponse.getBody();
        assertThat(approved).isNotNull();
        assertThat(approved.getStatus()).isEqualTo("ACTIVE");
        assertThat(approved.getCurrentRound()).isEqualTo(1);
        assertThat(approved.getCurrentRoundEndsAt()).isNotNull();

        // ── Step 3: Vote on all 4 round-1 matchups + WebSocket verification ───
        // Connect to WebSocket before voting
        BlockingQueue<Map<String, Object>> wsMessages = new LinkedBlockingQueue<>();
        WebSocketStompClient stompClient = buildStompClient();
        StompSession session = connectStompForTournament(stompClient, tournamentId, wsMessages);

        try {
            // Fetch the tournament to get matchup IDs
            TournamentResponse activeT = getTournament(tournamentId);
            List<MatchupResponse> round1Matchups = activeT.getMatchups().stream()
                    .filter(m -> m.getRoundNumber() == 1)
                    .collect(Collectors.toList());
            assertThat(round1Matchups).hasSize(4);

            // Vote on each round-1 matchup
            for (MatchupResponse matchup : round1Matchups) {
                Long matchupId = matchup.getId();
                Long chosenMemeId = matchup.getMemeA().getId(); // always vote for memeA

                Map<String, Object> votePayload = Map.of(
                        "matchupId", matchupId,
                        "chosenMemeId", chosenMemeId
                );

                ResponseEntity<BattleVoteResult> voteResponse = restTemplate.exchange(
                        baseUrl() + "/api/battle/vote/tournament",
                        HttpMethod.POST,
                        new HttpEntity<>(votePayload, authHeaders(userToken)),
                        BattleVoteResult.class
                );

                assertThat(voteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                BattleVoteResult voteResult = voteResponse.getBody();
                assertThat(voteResult).isNotNull();
                assertThat(voteResult.getMatchupId()).isEqualTo(matchupId);
                assertThat(voteResult.getMemeAVotes() + voteResult.getMemeBVotes()).isEqualTo(1);
            }

            // Verify WebSocket message received for at least one vote
            Map<String, Object> wsMessage = wsMessages.poll(5, TimeUnit.SECONDS);
            assertThat(wsMessage)
                    .as("Expected a WebSocket broadcast on /topic/battle/tournament/" + tournamentId)
                    .isNotNull();
            assertThat(wsMessage).containsKey("matchupId");
            assertThat(wsMessage).containsKey("votesA");
            assertThat(wsMessage).containsKey("votesB");

            // Verify duplicate vote returns 409
            MatchupResponse firstMatchup = round1Matchups.get(0);
            Map<String, Object> duplicateVotePayload = Map.of(
                    "matchupId", firstMatchup.getId(),
                    "chosenMemeId", firstMatchup.getMemeA().getId()
            );
            ResponseEntity<String> duplicateResponse = restTemplate.exchange(
                    baseUrl() + "/api/battle/vote/tournament",
                    HttpMethod.POST,
                    new HttpEntity<>(duplicateVotePayload, authHeaders(userToken)),
                    String.class
            );
            assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        // ── Step 4: Advance round 1 → round 2 ────────────────────────────────
        // Set currentRoundEndsAt to the past so the scheduler picks it up
        setRoundEndsAtToPast(tournamentId);
        roundAdvancementScheduler.advanceExpiredRounds();

        TournamentResponse afterRound1 = getTournament(tournamentId);
        assertThat(afterRound1.getCurrentRound()).isEqualTo(2);
        List<MatchupResponse> round2Matchups = afterRound1.getMatchups().stream()
                .filter(m -> m.getRoundNumber() == 2)
                .collect(Collectors.toList());
        assertThat(round2Matchups).hasSize(2);

        // ── Step 5: Vote on round-2 matchups and advance to final ─────────────
        for (MatchupResponse matchup : round2Matchups) {
            Map<String, Object> votePayload = Map.of(
                    "matchupId", matchup.getId(),
                    "chosenMemeId", matchup.getMemeA().getId()
            );
            ResponseEntity<BattleVoteResult> voteResponse = restTemplate.exchange(
                    baseUrl() + "/api/battle/vote/tournament",
                    HttpMethod.POST,
                    new HttpEntity<>(votePayload, authHeaders(userToken)),
                    BattleVoteResult.class
            );
            assertThat(voteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        setRoundEndsAtToPast(tournamentId);
        roundAdvancementScheduler.advanceExpiredRounds();

        TournamentResponse afterRound2 = getTournament(tournamentId);
        assertThat(afterRound2.getCurrentRound()).isEqualTo(3);
        List<MatchupResponse> finalMatchups = afterRound2.getMatchups().stream()
                .filter(m -> m.getRoundNumber() == 3)
                .collect(Collectors.toList());
        assertThat(finalMatchups).hasSize(1);

        // ── Step 6: Vote on final matchup and complete tournament ──────────────
        MatchupResponse finalMatchup = finalMatchups.get(0);
        Map<String, Object> finalVotePayload = Map.of(
                "matchupId", finalMatchup.getId(),
                "chosenMemeId", finalMatchup.getMemeA().getId()
        );
        ResponseEntity<BattleVoteResult> finalVoteResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/vote/tournament",
                HttpMethod.POST,
                new HttpEntity<>(finalVotePayload, authHeaders(userToken)),
                BattleVoteResult.class
        );
        assertThat(finalVoteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        setRoundEndsAtToPast(tournamentId);
        roundAdvancementScheduler.advanceExpiredRounds();

        // ── Step 7: Results page shows champion ───────────────────────────────
        TournamentResponse completed = getTournament(tournamentId);
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getChampion()).isNotNull();
        assertThat(completed.getChampion().getId()).isNotNull();

        // All matchups should have winners set
        List<MatchupResponse> allMatchups = completed.getMatchups();
        assertThat(allMatchups).isNotEmpty();
        for (MatchupResponse matchup : allMatchups) {
            assertThat(matchup.getWinner())
                    .as("Matchup %d (round %d) should have a winner", matchup.getId(), matchup.getRoundNumber())
                    .isNotNull();
        }
    }

    // =========================================================================
    // Test 2: Duplicate vote returns 409
    // =========================================================================

    /**
     * Verifies that voting twice on the same tournament matchup returns 409 Conflict.
     *
     * Validates: Requirement 8.1
     */
    @Test
    void tournamentVoting_duplicateVoteReturns409() {
        // Create and approve a tournament
        List<Long> memeIds = testMemes.stream().map(Meme::getId).collect(Collectors.toList());

        Map<String, Object> createRequest = Map.of(
                "name", PREFIX + "Duplicate Vote Test Tournament",
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
        Long tournamentId = createResponse.getBody().getId();

        restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                TournamentResponse.class
        );

        // Get the first round-1 matchup
        TournamentResponse activeT = getTournament(tournamentId);
        MatchupResponse matchup = activeT.getMatchups().stream()
                .filter(m -> m.getRoundNumber() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No round-1 matchup found"));

        Map<String, Object> votePayload = Map.of(
                "matchupId", matchup.getId(),
                "chosenMemeId", matchup.getMemeA().getId()
        );

        // First vote — should succeed
        ResponseEntity<BattleVoteResult> firstVote = restTemplate.exchange(
                baseUrl() + "/api/battle/vote/tournament",
                HttpMethod.POST,
                new HttpEntity<>(votePayload, authHeaders(userToken)),
                BattleVoteResult.class
        );
        assertThat(firstVote.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second vote on same matchup — should return 409
        ResponseEntity<String> secondVote = restTemplate.exchange(
                baseUrl() + "/api/battle/vote/tournament",
                HttpMethod.POST,
                new HttpEntity<>(votePayload, authHeaders(userToken)),
                String.class
        );
        assertThat(secondVote.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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

    private TournamentResponse getTournament(Long tournamentId) {
        ResponseEntity<TournamentResponse> response = restTemplate.exchange(
                baseUrl() + "/api/battle/tournaments/" + tournamentId,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                TournamentResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    /**
     * Sets the tournament's currentRoundEndsAt to 1 minute in the past so the
     * scheduler will pick it up on the next call.
     */
    private void setRoundEndsAtToPast(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new AssertionError("Tournament not found: " + tournamentId));
        tournament.setCurrentRoundEndsAt(LocalDateTime.now().minusMinutes(1));
        tournamentRepository.save(tournament);
    }

    /**
     * Builds a WebSocketStompClient backed by SockJS.
     */
    private WebSocketStompClient buildStompClient() {
        List<Transport> transports = Collections.singletonList(
                new WebSocketTransport(new StandardWebSocketClient())
        );
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    /**
     * Connects to the STOMP broker and subscribes to the tournament-specific topic.
     * Messages are placed into the provided BlockingQueue.
     */
    @SuppressWarnings("unchecked")
    private StompSession connectStompForTournament(WebSocketStompClient stompClient,
                                                    Long tournamentId,
                                                    BlockingQueue<Map<String, Object>> messages)
            throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";
        String topic = "/topic/battle/tournament/" + tournamentId;

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe(topic, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        messages.offer((Map<String, Object>) payload);
                    }
                });
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable exception) {
                // Log but don't fail — the test assertion on the queue will catch issues
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                // Log but don't fail
            }
        };

        StompSession session = stompClient
                .connectAsync(wsUrl, new WebSocketHttpHeaders(), sessionHandler)
                .get(10, TimeUnit.SECONDS);

        // Give the subscription a moment to register before triggering votes
        Thread.sleep(200);

        return session;
    }
}
