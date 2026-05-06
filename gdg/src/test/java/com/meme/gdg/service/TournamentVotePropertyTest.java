package com.meme.gdg.service;

import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.exception.DuplicateVoteException;
import com.meme.gdg.model.BattleVote;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.Tournament;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.model.TournamentStatus;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.BattlePairRepository;
import com.meme.gdg.repository.BattleVoteRepository;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for BattleVoteService.voteOnMatchup() duplicate enforcement.
 *
 * Validates: Requirements 8.2
 */
public class TournamentVotePropertyTest {

    /**
     * Property 3: One-vote-per-matchup enforcement.
     *
     * For any authenticated user and any TournamentMatchup, if that user has already cast
     * a BattleVote for that matchup, a second vote attempt SHALL be rejected
     * (DuplicateVoteException), and the vote counts for the matchup SHALL remain
     * unchanged (save() called exactly once — for the first vote only).
     *
     * **Validates: Requirements 8.2**
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 3: One-vote-per-matchup enforcement")
    void secondVoteOnSameMatchupThrowsDuplicateVoteException(
            @ForAll @Positive Long userId,
            @ForAll @Positive Long matchupId,
            @ForAll @Positive Long tournamentId,
            @ForAll @Positive Long memeAId,
            @ForAll @Positive Long memeBId) {

        // Ensure the two meme IDs are distinct so the matchup is valid
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

        // Build Tournament stub with ACTIVE status and current round = 1
        Tournament tournament = Tournament.builder()
                .id(tournamentId)
                .name("Test Tournament")
                .status(TournamentStatus.ACTIVE)
                .currentRound(1)
                .roundDurationHours(1)
                .memeCount(8)
                .build();

        // Build TournamentMatchup stub
        TournamentMatchup matchup = TournamentMatchup.builder()
                .id(matchupId)
                .tournament(tournament)
                .roundNumber(1)
                .bracketPosition(1)
                .memeA(memeA)
                .memeB(memeB)
                .votesA(0)
                .votesB(0)
                .build();

        // Build User stub
        User user = new User();
        user.setId(userId);

        // Stub repository lookups
        when(tournamentMatchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Stub save() to return a persisted BattleVote
        BattleVote savedVote = BattleVote.builder()
                .id(1L)
                .user(user)
                .matchup(matchup)
                .chosenMeme(memeA)
                .build();
        when(battleVoteRepository.save(any(BattleVote.class))).thenReturn(savedVote);

        // Stub tournamentMatchupRepository.save() to return the matchup
        when(tournamentMatchupRepository.save(any(TournamentMatchup.class))).thenReturn(matchup);

        // --- First vote: no existing vote → should succeed ---
        when(battleVoteRepository.findByUserIdAndMatchupId(userId, matchupId))
                .thenReturn(Optional.empty());

        service.voteOnMatchup(userId, matchupId, memeAId);

        // Verify save() was called exactly once for the first vote
        verify(battleVoteRepository, times(1)).save(any(BattleVote.class));

        // Verify matchup was updated once (votesA incremented)
        verify(tournamentMatchupRepository, times(1)).save(any(TournamentMatchup.class));

        // --- Second vote: existing vote present → should throw DuplicateVoteException ---
        when(battleVoteRepository.findByUserIdAndMatchupId(userId, matchupId))
                .thenReturn(Optional.of(savedVote));

        assertThatThrownBy(() -> service.voteOnMatchup(userId, matchupId, memeAId))
                .isInstanceOf(DuplicateVoteException.class)
                .hasMessageContaining("already voted on this matchup");

        // Assert save() was NOT called again — vote counts remain unchanged
        verify(battleVoteRepository, times(1)).save(any(BattleVote.class));

        // Assert matchup was NOT updated again
        verify(tournamentMatchupRepository, times(1)).save(any(TournamentMatchup.class));
    }
}
