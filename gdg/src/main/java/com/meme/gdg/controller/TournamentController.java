package com.meme.gdg.controller;

import com.meme.gdg.dto.MessageResponse;
import com.meme.gdg.dto.TournamentCreateRequest;
import com.meme.gdg.dto.TournamentResponse;
import com.meme.gdg.dto.TournamentSummaryResponse;
import com.meme.gdg.security.UserPrincipal;
import com.meme.gdg.service.TournamentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/battle/tournaments")
@Slf4j
public class TournamentController {

    private final TournamentService tournamentService;

    @Autowired
    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    /**
     * POST /api/battle/tournaments
     * Create a new tournament. Requires authentication (USER or ADMIN).
     * Requirement: 5.1
     */
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> createTournament(
            @Valid @RequestBody TournamentCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            TournamentResponse response = tournamentService.createTournament(userPrincipal.getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating tournament: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error creating tournament: " + e.getMessage()));
        }
    }

    /**
     * GET /api/battle/tournaments
     * List all tournaments with pagination. Public access.
     * Requirement: 6.1
     */
    @GetMapping
    public ResponseEntity<Page<TournamentSummaryResponse>> listTournaments(
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        Page<TournamentSummaryResponse> page = tournamentService.listTournaments(pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/battle/tournaments/my
     * Get tournaments created by the authenticated user. Requires authentication.
     * Requirement: 6.2
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<TournamentSummaryResponse>> getMyTournaments(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<TournamentSummaryResponse> tournaments = tournamentService.getMyTournaments(userPrincipal.getId());
        return ResponseEntity.ok(tournaments);
    }

    /**
     * GET /api/battle/tournaments/pending
     * Get all pending tournaments awaiting admin approval. Requires ADMIN role.
     * Requirement: 9.1
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TournamentSummaryResponse>> getPendingTournaments() {
        List<TournamentSummaryResponse> pending = tournamentService.getPendingTournaments();
        return ResponseEntity.ok(pending);
    }

    /**
     * GET /api/battle/tournaments/{id}
     * Get full tournament details including bracket. Public access.
     * Requirement: 6.3
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTournament(@PathVariable Long id) {
        try {
            TournamentResponse response = tournamentService.getTournament(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching tournament {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/battle/tournaments/{id}/approve
     * Approve a pending tournament. Requires ADMIN role.
     * Requirement: 10.1
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveTournament(@PathVariable Long id) {
        try {
            TournamentResponse response = tournamentService.approveTournament(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error approving tournament {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error approving tournament: " + e.getMessage()));
        }
    }

    /**
     * POST /api/battle/tournaments/{id}/reject
     * Reject a pending tournament. Requires ADMIN role.
     * Requirement: 6.4
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectTournament(@PathVariable Long id) {
        try {
            TournamentResponse response = tournamentService.rejectTournament(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error rejecting tournament {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error rejecting tournament: " + e.getMessage()));
        }
    }
}
