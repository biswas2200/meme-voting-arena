package com.meme.gdg.scheduler;

import com.meme.gdg.model.Meme;
import com.meme.gdg.model.Tournament;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.model.TournamentStatus;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled component that advances tournament rounds when their timers expire.
 *
 * <p>Runs every 30 seconds (fixedDelay = 30 000 ms). For each ACTIVE tournament
 * whose {@code currentRoundEndsAt} is in the past:
 * <ol>
 *   <li>Determines the winner of every matchup in the current round (higher vote
 *       count wins; lower meme ID wins on a tie).</li>
 *   <li>If this is not the final round, creates the next round's matchups by
 *       pairing winners in bracket order and starts the next round timer.</li>
 *   <li>If this is the final round, marks the tournament {@code COMPLETED},
 *       sets the {@code champion} and {@code completedAt} fields.</li>
 *   <li>Broadcasts the advancement event to
 *       {@code /topic/battle/tournament/{tournamentId}}.</li>
 * </ol>
 *
 * <p>Each tournament is processed inside its own try-catch so that a failure for
 * one tournament does not block the others.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoundAdvancementScheduler {

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchupRepository tournamentMatchupRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Polls for expired tournament rounds and advances them.
     * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void advanceExpiredRounds() {
        List<Tournament> expiredTournaments =
                tournamentRepository.findByStatusAndCurrentRoundEndsAtBefore(
                        TournamentStatus.ACTIVE, LocalDateTime.now());

        for (Tournament tournament : expiredTournaments) {
            try {
                advanceTournament(tournament);
            } catch (Exception e) {
                log.error("Failed to advance round for tournament id={}: {}",
                        tournament.getId(), e.getMessage(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void advanceTournament(Tournament tournament) {
        Long tournamentId = tournament.getId();
        int currentRound = tournament.getCurrentRound();

        log.info("Advancing tournament id={} from round {}", tournamentId, currentRound);

        // Fetch all matchups for the current round
        List<TournamentMatchup> matchups =
                tournamentMatchupRepository.findByTournamentIdAndRoundNumber(
                        tournamentId, currentRound);

        if (matchups.isEmpty()) {
            log.warn("No matchups found for tournament id={} round={}", tournamentId, currentRound);
            return;
        }

        // Determine and persist winners for each matchup
        List<Meme> winners = new ArrayList<>();
        for (TournamentMatchup matchup : matchups) {
            Meme winner = determineWinner(matchup);
            matchup.setWinner(winner);
            tournamentMatchupRepository.save(matchup);
            winners.add(winner);
        }

        // Total rounds = log₂(memeCount)
        int totalRounds = (int) (Math.log(tournament.getMemeCount()) / Math.log(2));
        boolean isFinalRound = (currentRound == totalRounds);

        if (isFinalRound) {
            // The single remaining winner is the champion
            Meme champion = winners.get(0);
            tournament.setStatus(TournamentStatus.COMPLETED);
            tournament.setChampion(champion);
            tournament.setCompletedAt(LocalDateTime.now());
            tournamentRepository.save(tournament);

            log.info("Tournament id={} COMPLETED. Champion meme id={}",
                    tournamentId, champion.getId());
        } else {
            // Create next round matchups by pairing winners in bracket order
            int nextRound = currentRound + 1;
            List<TournamentMatchup> nextMatchups = createNextRoundMatchups(
                    tournament, winners, nextRound);
            tournamentMatchupRepository.saveAll(nextMatchups);

            // Advance the round counter and reset the timer
            tournament.setCurrentRound(nextRound);
            tournament.setCurrentRoundEndsAt(
                    LocalDateTime.now().plusHours(tournament.getRoundDurationHours()));
            tournamentRepository.save(tournament);

            log.info("Tournament id={} advanced to round {}. Created {} matchups.",
                    tournamentId, nextRound, nextMatchups.size());
        }

        // Broadcast round advancement
        broadcastRoundAdvancement(tournament, matchups);
    }

    /**
     * Determines the winner of a matchup.
     * Higher vote count wins; on a tie, the meme with the lower ID wins.
     * Requirements: 7.1, 7.2
     */
    Meme determineWinner(TournamentMatchup matchup) {
        int votesA = matchup.getVotesA();
        int votesB = matchup.getVotesB();
        Meme memeA = matchup.getMemeA();
        Meme memeB = matchup.getMemeB();

        if (votesA > votesB) {
            return memeA;
        } else if (votesB > votesA) {
            return memeB;
        } else {
            // Tie: lower meme ID wins (deterministic tiebreaker)
            return memeA.getId() < memeB.getId() ? memeA : memeB;
        }
    }

    /**
     * Creates next-round matchups by pairing winners in bracket order.
     * Winner at position 1 faces winner at position 2, position 3 faces position 4, etc.
     * Requirements: 7.3
     */
    private List<TournamentMatchup> createNextRoundMatchups(
            Tournament tournament, List<Meme> winners, int nextRound) {

        List<TournamentMatchup> nextMatchups = new ArrayList<>();
        for (int i = 0; i < winners.size(); i += 2) {
            int bracketPosition = (i / 2) + 1; // 1-indexed
            TournamentMatchup matchup = TournamentMatchup.builder()
                    .tournament(tournament)
                    .roundNumber(nextRound)
                    .bracketPosition(bracketPosition)
                    .memeA(winners.get(i))
                    .memeB(winners.get(i + 1))
                    .build();
            nextMatchups.add(matchup);
        }
        return nextMatchups;
    }

    /**
     * Broadcasts round advancement details to the tournament's WebSocket topic.
     * Requirements: 7.5
     */
    private void broadcastRoundAdvancement(Tournament tournament,
                                            List<TournamentMatchup> completedMatchups) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tournamentId", tournament.getId());
        payload.put("advancedFromRound", tournament.getCurrentRound());
        payload.put("newStatus", tournament.getStatus().name());

        List<Map<String, Object>> matchupResults = new ArrayList<>();
        for (TournamentMatchup matchup : completedMatchups) {
            Map<String, Object> matchupInfo = new HashMap<>();
            matchupInfo.put("matchupId", matchup.getId());
            matchupInfo.put("votesA", matchup.getVotesA());
            matchupInfo.put("votesB", matchup.getVotesB());
            matchupInfo.put("winnerId",
                    matchup.getWinner() != null ? matchup.getWinner().getId() : null);
            matchupResults.add(matchupInfo);
        }
        payload.put("matchups", matchupResults);

        messagingTemplate.convertAndSend(
                "/topic/battle/tournament/" + tournament.getId(), payload);
    }
}
