package com.meme.gdg.scheduler;

import com.meme.gdg.model.Meme;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Positive;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for round advancement winner count in RoundAdvancementScheduler.
 *
 * Validates: Requirements 7.1, 7.3
 */
public class RoundAdvancementSchedulerWinnerCountPropertyTest {

    /**
     * Property 7: Round advancement preserves winner count.
     *
     * For any completed tournament round with k matchups, the number of winners
     * determined SHALL equal k, and each winner SHALL be one of the two memes
     * from its respective matchup.
     *
     * Validates: Requirements 7.1, 7.3
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 7: Round advancement preserves winner count")
    void roundAdvancementPreservesWinnerCount(
            @ForAll("matchupSets") List<TournamentMatchup> matchups) {

        // --- Set up scheduler with mocked dependencies ---
        TournamentRepository tournamentRepository = Mockito.mock(TournamentRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        SimpMessagingTemplate messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);

        RoundAdvancementScheduler scheduler = new RoundAdvancementScheduler(
                tournamentRepository,
                tournamentMatchupRepository,
                messagingTemplate);

        // --- Determine winner for each matchup ---
        List<Meme> winners = new ArrayList<>();
        for (TournamentMatchup matchup : matchups) {
            Meme winner = scheduler.determineWinner(matchup);
            winners.add(winner);
        }

        // --- Assert 1: winners.size() == matchups.size() ---
        assertThat(winners)
                .as("Number of winners must equal number of matchups (k=%d)", matchups.size())
                .hasSize(matchups.size());

        // --- Assert 2: each winner is one of the two memes in its matchup ---
        for (int i = 0; i < matchups.size(); i++) {
            TournamentMatchup matchup = matchups.get(i);
            Meme winner = winners.get(i);
            Long memeAId = matchup.getMemeA().getId();
            Long memeBId = matchup.getMemeB().getId();

            assertThat(winner.getId())
                    .as("Winner of matchup %d (memeA.id=%d, memeB.id=%d) must be one of the two memes",
                            i, memeAId, memeBId)
                    .isIn(memeAId, memeBId);
        }
    }

    /**
     * Generates random sets of 1–8 matchups, each with distinct meme IDs and random vote counts.
     */
    @Provide
    Arbitrary<List<TournamentMatchup>> matchupSets() {
        // Use an AtomicLong to generate unique meme IDs across all matchups in a single sample
        return Arbitraries.integers().between(1, 8).flatMap(count -> {
            // Generate 'count' matchups, each with two distinct memes and random vote counts
            return Arbitraries.integers().between(0, 100).list().ofSize(count * 3)
                    .map(values -> {
                        List<TournamentMatchup> matchups = new ArrayList<>();
                        // Use a simple counter to ensure unique IDs within this sample
                        long idBase = 1;
                        for (int i = 0; i < count; i++) {
                            int votesA = values.get(i * 3);
                            int votesB = values.get(i * 3 + 1);
                            // Use offset to ensure memeA and memeB have distinct IDs
                            long memeAId = idBase++;
                            long memeBId = idBase++;

                            Meme memeA = new Meme();
                            memeA.setId(memeAId);
                            memeA.setTitle("Meme A-" + memeAId);
                            memeA.setImageUrl("http://example.com/" + memeAId + ".jpg");
                            memeA.setVoteCount(0);

                            Meme memeB = new Meme();
                            memeB.setId(memeBId);
                            memeB.setTitle("Meme B-" + memeBId);
                            memeB.setImageUrl("http://example.com/" + memeBId + ".jpg");
                            memeB.setVoteCount(0);

                            TournamentMatchup matchup = TournamentMatchup.builder()
                                    .memeA(memeA)
                                    .memeB(memeB)
                                    .votesA(votesA)
                                    .votesB(votesB)
                                    .build();
                            matchups.add(matchup);
                        }
                        return matchups;
                    });
        });
    }
}
