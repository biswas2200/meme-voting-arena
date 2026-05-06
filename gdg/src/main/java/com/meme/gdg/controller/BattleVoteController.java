package com.meme.gdg.controller;

import com.meme.gdg.dto.BattleVoteResult;
import com.meme.gdg.dto.MessageResponse;
import com.meme.gdg.security.UserPrincipal;
import com.meme.gdg.service.BattleVoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/battle/vote")
@Slf4j
public class BattleVoteController {

    private final BattleVoteService battleVoteService;

    @Autowired
    public BattleVoteController(BattleVoteService battleVoteService) {
        this.battleVoteService = battleVoteService;
    }

    /**
     * POST /api/battle/vote/quick
     * Cast a vote on a Quick Battle pair.
     * Requirements: 2.1
     */
    @PostMapping("/quick")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> voteOnQuickBattle(
            @RequestBody QuickBattleVoteRequest request,
            Authentication authentication) {
        try {
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
            BattleVoteResult result = battleVoteService.voteOnPair(
                    userDetails.getId(),
                    request.getBattlePairId(),
                    request.getChosenMemeId()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error voting on quick battle pair: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * POST /api/battle/vote/tournament
     * Cast a vote on a tournament matchup.
     * Requirements: 8.1
     */
    @PostMapping("/tournament")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> voteOnTournamentMatchup(
            @RequestBody TournamentMatchupVoteRequest request,
            Authentication authentication) {
        try {
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
            BattleVoteResult result = battleVoteService.voteOnMatchup(
                    userDetails.getId(),
                    request.getMatchupId(),
                    request.getChosenMemeId()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error voting on tournament matchup: {}", e.getMessage());
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Inner request DTOs (kept local to avoid polluting the dto package with
    // trivial request wrappers; can be promoted to top-level classes if reused)
    // -------------------------------------------------------------------------

    @lombok.Data
    public static class QuickBattleVoteRequest {
        private Long battlePairId;
        private Long chosenMemeId;
    }

    @lombok.Data
    public static class TournamentMatchupVoteRequest {
        private Long matchupId;
        private Long chosenMemeId;
    }
}
