package com.meme.gdg.service;

import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.exception.DuplicateVoteException;
import com.meme.gdg.model.BattlePair;
import com.meme.gdg.model.BattleVote;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.BattlePairRepository;
import com.meme.gdg.repository.BattleVoteRepository;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for BattleVoteService.voteOnPair() duplicate enforcement
 * and vote count consistency.
 *
 * Validates: Requirements 2.2, 2.3, 2.4
 */
public class BattleVoteServicePropertyTest {

    /**
     * Property 2: One-vote-per-pair enforcement.
     *
     * For any authenticated user and any BattlePair, if that user has already cast
     * a BattleVote for that pair, a second vote attempt SHALL be rejected
     * (DuplicateVoteException), and the vote counts for the pair SHALL remain
     * unchanged (save() called exactly once — for the first vote only).
     *
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 2: One-vote-per-pair enforcement")
    void secondVoteOnSamePairThrowsDuplicateVoteException(
            @ForAll @Positive Long userId,
            @ForAll @Positive Long battlePairId,
            @ForAll @Positive Long memeAId,
            @ForAll @Positive Long memeBId) {

        // Ensure the two meme IDs are distinct so the pair is valid
        Assume.that(!memeAId.equals(memeBId));

        // --- Set up fresh mocks for each property try ---
        BattleVoteRepository battleVoteRepository = Mockito.mock(BattleVoteRepository.class);
        BattlePairRepository battlePairRepository = Mockito.mock(BattlePairRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        BattleVoteServiceImpl service = new BattleVoteServiceImpl(
                battleVoteRepository,
                battlePairRepository,
                tournamentMatchupRepository,
                userRepository,
                messagingTemplate);

        // Build meme stubs
        Meme memeA = new Meme();
        memeA.setId(memeAId);
        memeA.setTitle("Meme A");
        memeA.setImageUrl("http://example.com/a.jpg");
        memeA.setVoteCount(0);

        Meme memeB = new Meme();
        memeB.setId(memeBId);
        memeB.setTitle("Meme B");
        memeB.setImageUrl("http://example.com/b.jpg");
        memeB.setVoteCount(0);

        // Build BattlePair stub
        BattlePair battlePair = new BattlePair();
        battlePair.setId(battlePairId);
        battlePair.setMemeA(memeA);
        battlePair.setMemeB(memeB);

        // Build User stub
        User user = new User();
        user.setId(userId);

        // Stub repository lookups
        when(battlePairRepository.findById(battlePairId)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Stub vote counts (used after a successful first vote)
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(anyLong(), anyLong())).thenReturn(1);

        // Stub save() to return a persisted BattleVote
        BattleVote savedVote = BattleVote.builder()
                .id(1L)
                .user(user)
                .battlePair(battlePair)
                .chosenMeme(memeA)
                .build();
        when(battleVoteRepository.save(any(BattleVote.class))).thenReturn(savedVote);

        // --- First vote: no existing vote → should succeed ---
        when(battleVoteRepository.findByUserIdAndBattlePairId(userId, battlePairId))
                .thenReturn(Optional.empty());

        service.voteOnPair(userId, battlePairId, memeAId);

        // Verify save() was called exactly once for the first vote
        verify(battleVoteRepository, times(1)).save(any(BattleVote.class));

        // --- Second vote: existing vote present → should throw DuplicateVoteException ---
        when(battleVoteRepository.findByUserIdAndBattlePairId(userId, battlePairId))
                .thenReturn(Optional.of(savedVote));

        assertThatThrownBy(() -> service.voteOnPair(userId, battlePairId, memeAId))
                .isInstanceOf(DuplicateVoteException.class);

        // Assert save() was NOT called again — vote counts remain unchanged
        verify(battleVoteRepository, times(1)).save(any(BattleVote.class));
    }

    /**
     * Property 4: Vote count consistency after a Quick Battle vote.
     *
     * For any BattlePair and any valid sequence of votes from distinct users,
     * the sum votesA + votesB returned in BattleVoteResult SHALL equal the total
     * number of BattleVote records persisted for that pair.
     *
     * Strategy:
     * - Generate a random list of distinct user IDs (the voters)
     * - Each user votes for either memeA or memeB (randomly assigned)
     * - After all votes are cast, assert votesA + votesB == total votes cast
     * - The mock tracks persisted votes in an in-memory list so countBy* returns
     *   the real accumulated count, mirroring what the DB would return.
     *
     * Validates: Requirements 2.3, 2.4
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 4: Vote count consistency after a Quick Battle vote")
    void voteCountSumEqualsPersistedVoteRecords(
            @ForAll @Positive Long battlePairId,
            @ForAll @Positive Long memeAId,
            @ForAll @Positive Long memeBId,
            @ForAll @Size(min = 1, max = 20) List<@Positive Long> rawUserIds) {

        // Ensure the two meme IDs are distinct so the pair is valid
        Assume.that(!memeAId.equals(memeBId));

        // Deduplicate user IDs — each user may only vote once per pair
        List<Long> userIds = rawUserIds.stream().distinct().collect(Collectors.toList());
        Assume.that(!userIds.isEmpty());

        // --- Set up fresh mocks ---
        BattleVoteRepository battleVoteRepository = Mockito.mock(BattleVoteRepository.class);
        BattlePairRepository battlePairRepository = Mockito.mock(BattlePairRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        BattleVoteServiceImpl service = new BattleVoteServiceImpl(
                battleVoteRepository,
                battlePairRepository,
                tournamentMatchupRepository,
                userRepository,
                messagingTemplate);

        // Build meme stubs
        Meme memeA = new Meme();
        memeA.setId(memeAId);
        memeA.setTitle("Meme A");
        memeA.setImageUrl("http://example.com/a.jpg");
        memeA.setVoteCount(0);

        Meme memeB = new Meme();
        memeB.setId(memeBId);
        memeB.setTitle("Meme B");
        memeB.setImageUrl("http://example.com/b.jpg");
        memeB.setVoteCount(0);

        // Build BattlePair stub
        BattlePair battlePair = new BattlePair();
        battlePair.setId(battlePairId);
        battlePair.setMemeA(memeA);
        battlePair.setMemeB(memeB);

        when(battlePairRepository.findById(battlePairId)).thenReturn(Optional.of(battlePair));

        // In-memory vote store: tracks which meme each user voted for
        // Key = userId, Value = chosen meme ID
        Map<Long, Long> persistedVotes = new HashMap<>();

        // Stub userRepository to return a User for any ID
        when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long uid = invocation.getArgument(0);
            User u = new User();
            u.setId(uid);
            return Optional.of(u);
        });

        // Stub findByUserIdAndBattlePairId: returns empty if user hasn't voted yet
        when(battleVoteRepository.findByUserIdAndBattlePairId(anyLong(), eq(battlePairId)))
                .thenAnswer(invocation -> {
                    Long uid = invocation.getArgument(0);
                    if (persistedVotes.containsKey(uid)) {
                        BattleVote existing = new BattleVote();
                        existing.setId(uid); // use userId as a unique stub ID
                        return Optional.of(existing);
                    }
                    return Optional.empty();
                });

        // Stub save(): record the vote in the in-memory store
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(invocation -> {
            BattleVote v = invocation.getArgument(0);
            persistedVotes.put(v.getUser().getId(), v.getChosenMeme().getId());
            v.setId((long) persistedVotes.size());
            return v;
        });

        // Stub countByBattlePairIdAndChosenMemeId: count from in-memory store
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(eq(battlePairId), anyLong()))
                .thenAnswer(invocation -> {
                    Long chosenId = invocation.getArgument(1);
                    return (int) persistedVotes.values().stream()
                            .filter(chosenId::equals)
                            .count();
                });

        // --- Cast votes from each distinct user ---
        // Alternate votes between memeA and memeB to get a realistic mix
        BattleVoteResult lastResult = null;
        int expectedVotesA = 0;
        int expectedVotesB = 0;

        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            Long chosenMemeId = (i % 2 == 0) ? memeAId : memeBId;

            lastResult = service.voteOnPair(userId, battlePairId, chosenMemeId);

            if (chosenMemeId.equals(memeAId)) {
                expectedVotesA++;
            } else {
                expectedVotesB++;
            }
        }

        // --- Assert vote count consistency ---
        int totalPersistedVotes = persistedVotes.size();

        assertThat(lastResult).isNotNull();

        // votesA + votesB in the last result must equal total persisted records
        assertThat(lastResult.getMemeAVotes() + lastResult.getMemeBVotes())
                .as("votesA + votesB must equal total persisted BattleVote records for the pair")
                .isEqualTo(totalPersistedVotes);

        // Individual counts must also match the expected tallies
        assertThat(lastResult.getMemeAVotes())
                .as("votesA must equal the number of votes cast for memeA")
                .isEqualTo(expectedVotesA);

        assertThat(lastResult.getMemeBVotes())
                .as("votesB must equal the number of votes cast for memeB")
                .isEqualTo(expectedVotesB);

        // Total persisted records must equal the number of distinct users who voted
        assertThat(totalPersistedVotes)
                .as("total persisted BattleVote records must equal number of distinct voters")
                .isEqualTo(userIds.size());
    }
}
