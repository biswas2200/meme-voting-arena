package com.meme.gdg.service;

import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.exception.DuplicateVoteException;
import com.meme.gdg.exception.TournamentStateException;
import com.meme.gdg.model.BattlePair;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class BattleVoteServiceImpl implements BattleVoteService {

    private final BattleVoteRepository battleVoteRepository;
    private final BattlePairRepository battlePairRepository;
    private final TournamentMatchupRepository tournamentMatchupRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public BattleVoteServiceImpl(BattleVoteRepository battleVoteRepository,
                                  BattlePairRepository battlePairRepository,
                                  TournamentMatchupRepository tournamentMatchupRepository,
                                  UserRepository userRepository,
                                  SimpMessagingTemplate messagingTemplate) {
        this.battleVoteRepository = battleVoteRepository;
        this.battlePairRepository = battlePairRepository;
        this.tournamentMatchupRepository = tournamentMatchupRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public BattleVoteResult voteOnPair(Long userId, Long battlePairId, Long chosenMemeId) {
        // Look up the BattlePair
        BattlePair battlePair = battlePairRepository.findById(battlePairId)
                .orElseThrow(() -> new RuntimeException("Battle pair not found: " + battlePairId));

        // Look up the User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Check for existing vote — throw DuplicateVoteException if present
        battleVoteRepository.findByUserIdAndBattlePairId(userId, battlePairId)
                .ifPresent(v -> {
                    throw new DuplicateVoteException("You have already voted on this battle pair");
                });

        // Validate that chosenMemeId is one of the two memes in the pair
        Meme memeA = battlePair.getMemeA();
        Meme memeB = battlePair.getMemeB();
        if (!chosenMemeId.equals(memeA.getId()) && !chosenMemeId.equals(memeB.getId())) {
            throw new RuntimeException("Chosen meme is not part of this battle pair");
        }

        // Determine the chosen Meme entity
        Meme chosenMeme = chosenMemeId.equals(memeA.getId()) ? memeA : memeB;

        // Persist the new BattleVote
        BattleVote vote = BattleVote.builder()
                .user(user)
                .battlePair(battlePair)
                .chosenMeme(chosenMeme)
                .build();
        battleVoteRepository.save(vote);

        // Count votes for memeA and memeB in this pair
        int votesA = battleVoteRepository.countByBattlePairIdAndChosenMemeId(battlePairId, memeA.getId());
        int votesB = battleVoteRepository.countByBattlePairIdAndChosenMemeId(battlePairId, memeB.getId());

        // Broadcast vote update to /topic/battle/quick
        Map<String, Object> payload = new HashMap<>();
        payload.put("pairId", battlePairId);
        payload.put("memeAId", memeA.getId());
        payload.put("memeBId", memeB.getId());
        payload.put("votesA", votesA);
        payload.put("votesB", votesB);
        messagingTemplate.convertAndSend("/topic/battle/quick", payload);

        // Return BattleVoteResult with updated counts
        BattleVoteResult result = new BattleVoteResult();
        result.setPairId(battlePairId);
        result.setMemeAVotes(votesA);
        result.setMemeBVotes(votesB);
        result.setChosenMemeId(chosenMemeId);
        return result;
    }

    @Override
    public BattleVoteResult voteOnMatchup(Long userId, Long matchupId, Long chosenMemeId) {
        // Look up the TournamentMatchup
        TournamentMatchup matchup = tournamentMatchupRepository.findById(matchupId)
                .orElseThrow(() -> new RuntimeException("Tournament matchup not found: " + matchupId));

        // Look up the Tournament
        Tournament tournament = matchup.getTournament();

        // Validate tournament status is ACTIVE
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw new TournamentStateException("Tournament is not currently active");
        }

        // Validate matchup belongs to current active round
        if (matchup.getRoundNumber() != tournament.getCurrentRound()) {
            throw new TournamentStateException("This matchup is not in the current active round");
        }

        // Look up the User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Check for existing vote — throw DuplicateVoteException if present
        battleVoteRepository.findByUserIdAndMatchupId(userId, matchupId)
                .ifPresent(v -> {
                    throw new DuplicateVoteException("You have already voted on this matchup");
                });

        // Validate that chosenMemeId is one of the two memes in the matchup
        Meme memeA = matchup.getMemeA();
        Meme memeB = matchup.getMemeB();
        if (!chosenMemeId.equals(memeA.getId()) && !chosenMemeId.equals(memeB.getId())) {
            throw new RuntimeException("Chosen meme is not part of this matchup");
        }

        // Determine the chosen Meme entity
        Meme chosenMeme = chosenMemeId.equals(memeA.getId()) ? memeA : memeB;

        // Persist the new BattleVote with matchup reference
        BattleVote vote = BattleVote.builder()
                .user(user)
                .matchup(matchup)
                .chosenMeme(chosenMeme)
                .build();
        battleVoteRepository.save(vote);

        // Update TournamentMatchup.votesA or votesB based on chosen meme
        if (chosenMemeId.equals(memeA.getId())) {
            matchup.setVotesA(matchup.getVotesA() + 1);
        } else {
            matchup.setVotesB(matchup.getVotesB() + 1);
        }
        tournamentMatchupRepository.save(matchup);

        // Get updated vote counts
        int votesA = matchup.getVotesA();
        int votesB = matchup.getVotesB();

        // Broadcast vote update to /topic/battle/tournament/{tournamentId}
        Map<String, Object> payload = new HashMap<>();
        payload.put("matchupId", matchupId);
        payload.put("votesA", votesA);
        payload.put("votesB", votesB);
        payload.put("winnerId", matchup.getWinner() != null ? matchup.getWinner().getId() : null);
        messagingTemplate.convertAndSend("/topic/battle/tournament/" + tournament.getId(), payload);

        // Return BattleVoteResult with updated counts
        BattleVoteResult result = new BattleVoteResult();
        result.setMatchupId(matchupId);
        result.setMemeAVotes(votesA);
        result.setMemeBVotes(votesB);
        result.setChosenMemeId(chosenMemeId);
        return result;
    }
}
