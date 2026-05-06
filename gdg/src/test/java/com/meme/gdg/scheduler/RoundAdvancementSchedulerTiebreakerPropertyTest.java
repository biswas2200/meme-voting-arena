package com.meme.gdg.scheduler;

import com.meme.gdg.model.Meme;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for the tiebreaker logic in RoundAdvancementScheduler.
 *
 * Validates: Requirements 7.2
 */
public class RoundAdvancementSchedulerTiebreakerPropertyTest {

    /**
     * Property 5: Tiebreaker selects lower meme ID.
     *
     * For any TournamentMatchup where votesA == votesB at round expiry,
     * the winner determined by RoundAdvancementScheduler SHALL be the meme
     * with the lower ID.
     *
     * Validates: Requirements 7.2
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 5: Tiebreaker selects lower meme ID")
    void tiebreakerSelectsLowerMemeId(
            @ForAll @Positive Long memeAId,
            @ForAll @Positive Long memeBId,
            @ForAll @Positive int tiedVoteCount) {

        // Ensure the two meme IDs are distinct so the matchup is valid
        Assume.that(!memeAId.equals(memeBId));

        // --- Set up scheduler with mocked dependencies ---
        TournamentRepository tournamentRepository = Mockito.mock(TournamentRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        RoundAdvancementScheduler scheduler = new RoundAdvancementScheduler(
                tournamentRepository,
                tournamentMatchupRepository,
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

        // Build a matchup with equal vote counts (tie scenario)
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA)
                .memeB(memeB)
                .votesA(tiedVoteCount)
                .votesB(tiedVoteCount)
                .build();

        // --- Invoke the tiebreaker logic ---
        Meme winner = scheduler.determineWinner(matchup);

        // --- Assert: winner has the lower meme ID ---
        long expectedWinnerId = Math.min(memeAId, memeBId);

        assertThat(winner.getId())
                .as("On a tie (votesA=%d == votesB=%d), winner must be the meme with the lower ID (min(%d, %d) = %d)",
                        tiedVoteCount, tiedVoteCount, memeAId, memeBId, expectedWinnerId)
                .isEqualTo(expectedWinnerId);
    }
}
