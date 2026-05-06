package com.meme.gdg.service;

import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.exception.DuplicateVoteException;
import com.meme.gdg.exception.TournamentStateException;
import com.meme.gdg.model.*;
import com.meme.gdg.repository.BattlePairRepository;
import com.meme.gdg.repository.BattleVoteRepository;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BattleVoteServiceImpl — voteOnPair and voteOnMatchup.
 */
@ExtendWith(MockitoExtension.class)
class BattleVoteServiceUnitTest {

    @Mock private BattleVoteRepository battleVoteRepository;
    @Mock private BattlePairRepository battlePairRepository;
    @Mock private TournamentMatchupRepository tournamentMatchupRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private BattleVoteServiceImpl battleVoteService;

    private User user;
    private Meme memeA;
    private Meme memeB;
    private BattlePair battlePair;
    private Tournament tournament;
    private TournamentMatchup matchup;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("voter");

        memeA = new Meme();
        memeA.setId(10L);
        memeA.setTitle("Meme A");
        memeA.setImageUrl("https://example.com/a.jpg");
        memeA.setVoteCount(0);

        memeB = new Meme();
        memeB.setId(20L);
        memeB.setTitle("Meme B");
        memeB.setImageUrl("https://example.com/b.jpg");
        memeB.setVoteCount(0);

        battlePair = new BattlePair();
        battlePair.setId(100L);
        battlePair.setMemeA(memeA);
        battlePair.setMemeB(memeB);

        tournament = new Tournament();
        tournament.setId(200L);
        tournament.setStatus(TournamentStatus.ACTIVE);
        tournament.setCurrentRound(1);
        tournament.setRoundDurationHours(1);
        tournament.setMemeCount(8);
        User creator = new User();
        creator.setId(99L);
        creator.setUsername("admin");
        tournament.setCreator(creator);

        matchup = TournamentMatchup.builder()
                .id(300L)
                .tournament(tournament)
                .roundNumber(1)
                .bracketPosition(1)
                .memeA(memeA)
                .memeB(memeB)
                .votesA(0)
                .votesB(0)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // voteOnPair — happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void voteOnPair_choosingMemeA_returnsResultWithPairIdAndChosenMemeId() {
        when(battlePairRepository.findById(100L)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndBattlePairId(1L, 100L)).thenReturn(Optional.empty());
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(100L, 10L)).thenReturn(1);
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(100L, 20L)).thenReturn(0);
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        BattleVoteResult result = battleVoteService.voteOnPair(1L, 100L, 10L);

        assertThat(result.getPairId()).isEqualTo(100L);
        assertThat(result.getChosenMemeId()).isEqualTo(10L);
        assertThat(result.getMemeAVotes()).isEqualTo(1);
        assertThat(result.getMemeBVotes()).isEqualTo(0);
    }

    @Test
    void voteOnPair_choosingMemeB_returnsCorrectVoteCounts() {
        when(battlePairRepository.findById(100L)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndBattlePairId(1L, 100L)).thenReturn(Optional.empty());
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(100L, 10L)).thenReturn(0);
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(100L, 20L)).thenReturn(1);
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        BattleVoteResult result = battleVoteService.voteOnPair(1L, 100L, 20L);

        assertThat(result.getChosenMemeId()).isEqualTo(20L);
        assertThat(result.getMemeAVotes()).isEqualTo(0);
        assertThat(result.getMemeBVotes()).isEqualTo(1);
    }

    @Test
    void voteOnPair_broadcastsToQuickBattleTopic() {
        when(battlePairRepository.findById(100L)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndBattlePairId(1L, 100L)).thenReturn(Optional.empty());
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(battleVoteRepository.countByBattlePairIdAndChosenMemeId(anyLong(), anyLong())).thenReturn(1);

        battleVoteService.voteOnPair(1L, 100L, 10L);

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/battle/quick"), any(Object.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // voteOnPair — error cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void voteOnPair_duplicateVote_throwsDuplicateVoteException() {
        when(battlePairRepository.findById(100L)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        BattleVote existingVote = BattleVote.builder().id(1L).user(user).battlePair(battlePair).chosenMeme(memeA).build();
        when(battleVoteRepository.findByUserIdAndBattlePairId(1L, 100L)).thenReturn(Optional.of(existingVote));

        assertThatThrownBy(() -> battleVoteService.voteOnPair(1L, 100L, 10L))
                .isInstanceOf(DuplicateVoteException.class)
                .hasMessageContaining("already voted");
    }

    @Test
    void voteOnPair_invalidMemeId_throwsRuntimeException() {
        when(battlePairRepository.findById(100L)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndBattlePairId(1L, 100L)).thenReturn(Optional.empty());

        // meme ID 999 is not part of the pair
        assertThatThrownBy(() -> battleVoteService.voteOnPair(1L, 100L, 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not part of this battle pair");
    }

    @Test
    void voteOnPair_battlePairNotFound_throwsRuntimeException() {
        when(battlePairRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> battleVoteService.voteOnPair(1L, 999L, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Battle pair not found");
    }

    @Test
    void voteOnPair_userNotFound_throwsRuntimeException() {
        when(battlePairRepository.findById(100L)).thenReturn(Optional.of(battlePair));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> battleVoteService.voteOnPair(999L, 100L, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // voteOnMatchup — happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void voteOnMatchup_choosingMemeA_incrementsVotesA() {
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndMatchupId(1L, 300L)).thenReturn(Optional.empty());
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tournamentMatchupRepository.save(any(TournamentMatchup.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        BattleVoteResult result = battleVoteService.voteOnMatchup(1L, 300L, 10L);

        assertThat(result.getMatchupId()).isEqualTo(300L);
        assertThat(result.getChosenMemeId()).isEqualTo(10L);
        assertThat(result.getMemeAVotes()).isEqualTo(1);
        assertThat(result.getMemeBVotes()).isEqualTo(0);
    }

    @Test
    void voteOnMatchup_choosingMemeB_incrementsVotesB() {
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndMatchupId(1L, 300L)).thenReturn(Optional.empty());
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tournamentMatchupRepository.save(any(TournamentMatchup.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        BattleVoteResult result = battleVoteService.voteOnMatchup(1L, 300L, 20L);

        assertThat(result.getMemeAVotes()).isEqualTo(0);
        assertThat(result.getMemeBVotes()).isEqualTo(1);
    }

    @Test
    void voteOnMatchup_broadcastsToTournamentTopic() {
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndMatchupId(1L, 300L)).thenReturn(Optional.empty());
        when(battleVoteRepository.save(any(BattleVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tournamentMatchupRepository.save(any(TournamentMatchup.class))).thenAnswer(inv -> inv.getArgument(0));

        battleVoteService.voteOnMatchup(1L, 300L, 10L);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/battle/tournament/200"), any(Object.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // voteOnMatchup — error cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void voteOnMatchup_tournamentNotActive_throwsTournamentStateException() {
        tournament.setStatus(TournamentStatus.PENDING_APPROVAL);
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));

        assertThatThrownBy(() -> battleVoteService.voteOnMatchup(1L, 300L, 10L))
                .isInstanceOf(TournamentStateException.class)
                .hasMessageContaining("not currently active");
    }

    @Test
    void voteOnMatchup_wrongRound_throwsTournamentStateException() {
        tournament.setCurrentRound(2); // matchup is round 1
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));

        assertThatThrownBy(() -> battleVoteService.voteOnMatchup(1L, 300L, 10L))
                .isInstanceOf(TournamentStateException.class)
                .hasMessageContaining("not in the current active round");
    }

    @Test
    void voteOnMatchup_duplicateVote_throwsDuplicateVoteException() {
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        BattleVote existingVote = BattleVote.builder().id(1L).user(user).matchup(matchup).chosenMeme(memeA).build();
        when(battleVoteRepository.findByUserIdAndMatchupId(1L, 300L)).thenReturn(Optional.of(existingVote));

        assertThatThrownBy(() -> battleVoteService.voteOnMatchup(1L, 300L, 10L))
                .isInstanceOf(DuplicateVoteException.class)
                .hasMessageContaining("already voted");
    }

    @Test
    void voteOnMatchup_invalidMemeId_throwsRuntimeException() {
        when(tournamentMatchupRepository.findById(300L)).thenReturn(Optional.of(matchup));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(battleVoteRepository.findByUserIdAndMatchupId(1L, 300L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> battleVoteService.voteOnMatchup(1L, 300L, 999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not part of this matchup");
    }
}
