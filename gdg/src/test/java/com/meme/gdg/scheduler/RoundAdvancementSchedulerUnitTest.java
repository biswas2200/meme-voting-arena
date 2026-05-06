package com.meme.gdg.scheduler;

import com.meme.gdg.model.Meme;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for RoundAdvancementScheduler.determineWinner() with concrete assertion values.
 */
@ExtendWith(MockitoExtension.class)
class RoundAdvancementSchedulerUnitTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentMatchupRepository tournamentMatchupRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoundAdvancementScheduler scheduler;

    private Meme memeA;
    private Meme memeB;

    @BeforeEach
    void setUp() {
        memeA = new Meme();
        memeA.setId(1L);
        memeA.setTitle("Meme A");
        memeA.setImageUrl("https://example.com/a.jpg");
        memeA.setVoteCount(0);

        memeB = new Meme();
        memeB.setId(2L);
        memeB.setTitle("Meme B");
        memeB.setImageUrl("https://example.com/b.jpg");
        memeB.setVoteCount(0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // determineWinner — clear winner
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void determineWinner_memeAHasMoreVotes_returnsMemeA() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(5).votesB(3).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(1L);
        assertThat(winner.getTitle()).isEqualTo("Meme A");
    }

    @Test
    void determineWinner_memeBHasMoreVotes_returnsMemeB() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(2).votesB(7).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(2L);
        assertThat(winner.getTitle()).isEqualTo("Meme B");
    }

    @Test
    void determineWinner_memeAHasOneMoreVote_returnsMemeA() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(10).votesB(9).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // determineWinner — tiebreaker (lower ID wins)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void determineWinner_tiedVotes_lowerIdWins_memeAHasLowerId() {
        // memeA.id=1, memeB.id=2 → memeA wins on tie
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(4).votesB(4).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(1L);
    }

    @Test
    void determineWinner_tiedVotes_lowerIdWins_memeBHasLowerId() {
        // Swap IDs: memeA.id=5, memeB.id=3 → memeB wins on tie
        memeA.setId(5L);
        memeB.setId(3L);
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(6).votesB(6).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(3L);
    }

    @Test
    void determineWinner_tiedAtZeroVotes_lowerIdWins() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(0).votesB(0).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(1L); // memeA.id=1 < memeB.id=2
    }

    @Test
    void determineWinner_tiedAtHighVoteCount_lowerIdWins() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(100).votesB(100).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // determineWinner — edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void determineWinner_memeAHasAllVotes_returnsMemeA() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(50).votesB(0).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(1L);
    }

    @Test
    void determineWinner_memeBHasAllVotes_returnsMemeB() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(0).votesB(50).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner.getId()).isEqualTo(2L);
    }

    @Test
    void determineWinner_returnsNonNullWinner() {
        TournamentMatchup matchup = TournamentMatchup.builder()
                .memeA(memeA).memeB(memeB).votesA(3).votesB(7).build();

        Meme winner = scheduler.determineWinner(matchup);

        assertThat(winner).isNotNull();
        assertThat(winner.getId()).isNotNull();
    }
}
