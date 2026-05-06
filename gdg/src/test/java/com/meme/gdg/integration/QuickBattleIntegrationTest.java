package com.meme.gdg.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meme.gdg.dto.BattlePairResponse;
import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.BattlePairRepository;
import com.meme.gdg.repository.BattleVoteRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the Quick Battle flow.
 *
 * Tests the full HTTP + WebSocket flow:
 * - Authenticated user requests a pair
 * - User votes on the pair
 * - WebSocket broadcast is received
 * - Duplicate vote returns 409
 * - New pair after vote has a different pairId
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 4.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class QuickBattleIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemeRepository memeRepository;

    @Autowired
    private BattlePairRepository battlePairRepository;

    @Autowired
    private BattleVoteRepository battleVoteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private String jwtToken;
    private Meme meme1;
    private Meme meme2;

    @BeforeEach
    void setUp() {
        // Clean up any leftover data from previous tests
        cleanUp();

        // Create a test user
        testUser = new User();
        testUser.setUsername("integration_test_user_" + System.currentTimeMillis());
        testUser.setEmail("integration_test_" + System.currentTimeMillis() + "@test.com");
        testUser.setPassword(passwordEncoder.encode("testpassword123"));
        testUser.setRole(User.Role.USER);
        testUser = userRepository.save(testUser);

        // Generate JWT token for the test user
        UserPrincipal userPrincipal = UserPrincipal.create(testUser);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        jwtToken = jwtUtils.generateJwtToken(authentication);

        // Seed at least 2 memes
        meme1 = new Meme();
        meme1.setTitle("Integration Test Meme 1");
        meme1.setImageUrl("https://example.com/meme1.jpg");
        meme1.setUploadedBy(testUser);
        meme1.setVoteCount(0);
        meme1 = memeRepository.save(meme1);

        meme2 = new Meme();
        meme2.setTitle("Integration Test Meme 2");
        meme2.setImageUrl("https://example.com/meme2.jpg");
        meme2.setUploadedBy(testUser);
        meme2.setVoteCount(0);
        meme2 = memeRepository.save(meme2);
    }

    @AfterEach
    void cleanUp() {
        // Clean up in dependency order
        battleVoteRepository.deleteAll();
        battlePairRepository.deleteAll();
        memeRepository.findAll().stream()
                .filter(m -> m.getTitle() != null && m.getTitle().startsWith("Integration Test Meme"))
                .forEach(memeRepository::delete);
        userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith("integration_test_user_"))
                .forEach(userRepository::delete);
    }

    /**
     * Test 1: Full Quick Battle flow
     *
     * Step 1: GET /api/battle/quick/pair → 200 OK with pairId, memeA, memeB (distinct IDs)
     * Step 2: Connect to WebSocket and subscribe to /topic/battle/quick
     * Step 3: POST /api/battle/vote/quick → 200 OK with updated vote counts
     * Step 4: Assert WebSocket message received with updated vote counts
     * Step 5: POST /api/battle/vote/quick again → 409 Conflict
     *
     * Validates: Requirements 2.1, 2.2, 2.3, 2.4
     */
    @Test
    void fullQuickBattleFlow_voteAndWebSocketBroadcastAndDuplicateVoteRejected() throws Exception {
        // ── Step 1: Get a battle pair ──────────────────────────────────────────
        HttpHeaders headers = authHeaders();
        ResponseEntity<BattlePairResponse> pairResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/quick/pair",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                BattlePairResponse.class
        );

        assertThat(pairResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        BattlePairResponse pair = pairResponse.getBody();
        assertThat(pair).isNotNull();
        assertThat(pair.getPairId()).isNotNull();
        assertThat(pair.getMemeA()).isNotNull();
        assertThat(pair.getMemeB()).isNotNull();
        assertThat(pair.getMemeA().getId()).isNotEqualTo(pair.getMemeB().getId());

        Long pairId = pair.getPairId();
        Long chosenMemeId = pair.getMemeA().getId();

        // ── Step 2: Connect to WebSocket and subscribe ─────────────────────────
        BlockingQueue<Map<String, Object>> wsMessages = new LinkedBlockingQueue<>();

        WebSocketStompClient stompClient = buildStompClient();
        StompSession session = connectStomp(stompClient, wsMessages);

        try {
            // ── Step 3: Cast a vote ────────────────────────────────────────────
            Map<String, Object> votePayload = Map.of(
                    "battlePairId", pairId,
                    "chosenMemeId", chosenMemeId
            );

            ResponseEntity<BattleVoteResult> voteResponse = restTemplate.exchange(
                    baseUrl() + "/api/battle/vote/quick",
                    HttpMethod.POST,
                    new HttpEntity<>(votePayload, headers),
                    BattleVoteResult.class
            );

            assertThat(voteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            BattleVoteResult voteResult = voteResponse.getBody();
            assertThat(voteResult).isNotNull();
            assertThat(voteResult.getPairId()).isEqualTo(pairId);
            assertThat(voteResult.getChosenMemeId()).isEqualTo(chosenMemeId);
            // Total votes should be 1 (memeAVotes + memeBVotes)
            assertThat(voteResult.getMemeAVotes() + voteResult.getMemeBVotes()).isEqualTo(1);

            // ── Step 4: Assert WebSocket message received ──────────────────────
            Map<String, Object> wsMessage = wsMessages.poll(5, TimeUnit.SECONDS);
            assertThat(wsMessage)
                    .as("Expected a WebSocket broadcast on /topic/battle/quick within 5 seconds")
                    .isNotNull();

            // The broadcast payload should contain the pairId and vote counts
            assertThat(wsMessage).containsKey("pairId");
            assertThat(wsMessage).containsKey("votesA");
            assertThat(wsMessage).containsKey("votesB");

            // pairId in the broadcast should match the pair we voted on
            Number broadcastPairId = (Number) wsMessage.get("pairId");
            assertThat(broadcastPairId.longValue()).isEqualTo(pairId);

            // Total broadcast votes should equal 1
            Number broadcastVotesA = (Number) wsMessage.get("votesA");
            Number broadcastVotesB = (Number) wsMessage.get("votesB");
            assertThat(broadcastVotesA.intValue() + broadcastVotesB.intValue()).isEqualTo(1);

            // ── Step 5: Duplicate vote → 409 Conflict ─────────────────────────
            ResponseEntity<String> duplicateVoteResponse = restTemplate.exchange(
                    baseUrl() + "/api/battle/vote/quick",
                    HttpMethod.POST,
                    new HttpEntity<>(votePayload, headers),
                    String.class
            );

            assertThat(duplicateVoteResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * Test 2: Auto-advance to next pair after vote
     *
     * After a vote, a new call to GET /api/battle/quick/pair returns a NEW pair
     * (different pairId), because each call creates a new BattlePair.
     *
     * Validates: Requirement 4.1
     */
    @Test
    void autoAdvance_newPairAfterVoteHasDifferentPairId() {
        HttpHeaders headers = authHeaders();

        // ── Step 1: Get first pair ─────────────────────────────────────────────
        ResponseEntity<BattlePairResponse> firstPairResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/quick/pair",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                BattlePairResponse.class
        );

        assertThat(firstPairResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        BattlePairResponse firstPair = firstPairResponse.getBody();
        assertThat(firstPair).isNotNull();
        Long firstPairId = firstPair.getPairId();

        // ── Step 2: Vote on the first pair ────────────────────────────────────
        Map<String, Object> votePayload = Map.of(
                "battlePairId", firstPairId,
                "chosenMemeId", firstPair.getMemeA().getId()
        );

        ResponseEntity<BattleVoteResult> voteResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/vote/quick",
                HttpMethod.POST,
                new HttpEntity<>(votePayload, headers),
                BattleVoteResult.class
        );

        assertThat(voteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ── Step 3: Get next pair — should have a different pairId ────────────
        ResponseEntity<BattlePairResponse> secondPairResponse = restTemplate.exchange(
                baseUrl() + "/api/battle/quick/pair",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                BattlePairResponse.class
        );

        assertThat(secondPairResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        BattlePairResponse secondPair = secondPairResponse.getBody();
        assertThat(secondPair).isNotNull();
        Long secondPairId = secondPair.getPairId();

        // Each call to /pair creates a new BattlePair with a new ID
        assertThat(secondPairId)
                .as("Each call to /api/battle/quick/pair should return a new BattlePair with a different pairId")
                .isNotEqualTo(firstPairId);

        // The new pair should also have two distinct memes
        assertThat(secondPair.getMemeA().getId()).isNotEqualTo(secondPair.getMemeB().getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        return headers;
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
     * Connects to the STOMP broker and subscribes to /topic/battle/quick.
     * Messages are placed into the provided BlockingQueue.
     */
    @SuppressWarnings("unchecked")
    private StompSession connectStomp(WebSocketStompClient stompClient,
                                      BlockingQueue<Map<String, Object>> messages) throws Exception {
        String wsUrl = "ws://localhost:" + port + "/ws";

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/topic/battle/quick", new StompFrameHandler() {
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

        // Give the subscription a moment to register before we trigger a vote
        Thread.sleep(200);

        return session;
    }
}
