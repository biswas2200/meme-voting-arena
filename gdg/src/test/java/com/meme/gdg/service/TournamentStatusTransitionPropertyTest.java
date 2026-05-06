package com.meme.gdg.service;

import com.meme.gdg.dto.TournamentResponse;
import com.meme.gdg.exception.TournamentStateException;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.Tournament;
import com.meme.gdg.model.TournamentStatus;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
import com.meme.gdg.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for tournament status transition validity.
 *
 * Validates: Requirements 6.1, 6.2, 6.5
 */
public class TournamentStatusTransitionPropertyTest {

    /**
     * Property 8: Tournament status transition validity.
     *
     * For any tournament, the status SHALL only transition along the valid paths:
     *   PENDING_APPROVAL → ACTIVE  (via approveTournament)
     *   PENDING_APPROVAL → REJECTED (via rejectTournament)
     *
     * Any attempt to approve or reject a tournament that is NOT in PENDING_APPROVAL
     * status SHALL throw TournamentStateException.
     *
     * Validates: Requirements 6.1, 6.2, 6.5
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 8: Tournament status transition validity")
    void validTransitionsSucceedAndInvalidTransitionsThrow(
            @ForAll("nonPendingStatusProvider") TournamentStatus invalidStartStatus,
            @ForAll("operationProvider") String operation,
            @ForAll @Positive Long tournamentId) {

        // --- Set up fresh mocks for each property try ---
        TournamentRepository tournamentRepository = Mockito.mock(TournamentRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        MemeRepository memeRepository = Mockito.mock(MemeRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        TournamentServiceImpl service = new TournamentServiceImpl(
                tournamentRepository,
                tournamentMatchupRepository,
                memeRepository,
                userRepository);

        // Build a tournament stub in the given non-PENDING_APPROVAL status
        Tournament tournament = buildTournamentStub(tournamentId, invalidStartStatus);

        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        // --- Assert: approve/reject on a non-PENDING_APPROVAL tournament throws TournamentStateException ---
        if ("approve".equals(operation)) {
            assertThatThrownBy(() -> service.approveTournament(tournamentId))
                    .as("approveTournament on a %s tournament must throw TournamentStateException", invalidStartStatus)
                    .isInstanceOf(TournamentStateException.class);
        } else {
            assertThatThrownBy(() -> service.rejectTournament(tournamentId))
                    .as("rejectTournament on a %s tournament must throw TournamentStateException", invalidStartStatus)
                    .isInstanceOf(TournamentStateException.class);
        }

        // --- Assert: status was NOT changed (still the original invalid start status) ---
        assertThat(tournament.getStatus())
                .as("Tournament status must remain %s after a failed transition attempt", invalidStartStatus)
                .isEqualTo(invalidStartStatus);
    }

    /**
     * Property 8 (valid path): PENDING_APPROVAL → ACTIVE via approveTournament.
     *
     * Validates: Requirement 6.1
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 8: PENDING_APPROVAL to ACTIVE transition is valid")
    void approveTournamentTransitionsPendingToActive(@ForAll @Positive Long tournamentId) {

        TournamentRepository tournamentRepository = Mockito.mock(TournamentRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        MemeRepository memeRepository = Mockito.mock(MemeRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        TournamentServiceImpl service = new TournamentServiceImpl(
                tournamentRepository,
                tournamentMatchupRepository,
                memeRepository,
                userRepository);

        // Build a tournament in PENDING_APPROVAL status
        Tournament tournament = buildTournamentStub(tournamentId, TournamentStatus.PENDING_APPROVAL);

        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        // --- Invoke approve ---
        TournamentResponse response = service.approveTournament(tournamentId);

        // --- Assert: status transitioned to ACTIVE ---
        assertThat(response.getStatus())
                .as("approveTournament must transition status from PENDING_APPROVAL to ACTIVE")
                .isEqualTo(TournamentStatus.ACTIVE.name());

        assertThat(tournament.getStatus())
                .as("Tournament entity status must be ACTIVE after approval")
                .isEqualTo(TournamentStatus.ACTIVE);

        // --- Assert: currentRound is set to 1 ---
        assertThat(response.getCurrentRound())
                .as("currentRound must be set to 1 after approval")
                .isEqualTo(1);

        // --- Assert: currentRoundEndsAt is set (not null) ---
        assertThat(response.getCurrentRoundEndsAt())
                .as("currentRoundEndsAt must be set after approval")
                .isNotNull();
    }

    /**
     * Property 8 (valid path): PENDING_APPROVAL → REJECTED via rejectTournament.
     *
     * Validates: Requirement 6.2
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 8: PENDING_APPROVAL to REJECTED transition is valid")
    void rejectTournamentTransitionsPendingToRejected(@ForAll @Positive Long tournamentId) {

        TournamentRepository tournamentRepository = Mockito.mock(TournamentRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        MemeRepository memeRepository = Mockito.mock(MemeRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        TournamentServiceImpl service = new TournamentServiceImpl(
                tournamentRepository,
                tournamentMatchupRepository,
                memeRepository,
                userRepository);

        // Build a tournament in PENDING_APPROVAL status
        Tournament tournament = buildTournamentStub(tournamentId, TournamentStatus.PENDING_APPROVAL);

        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        // --- Invoke reject ---
        TournamentResponse response = service.rejectTournament(tournamentId);

        // --- Assert: status transitioned to REJECTED ---
        assertThat(response.getStatus())
                .as("rejectTournament must transition status from PENDING_APPROVAL to REJECTED")
                .isEqualTo(TournamentStatus.REJECTED.name());

        assertThat(tournament.getStatus())
                .as("Tournament entity status must be REJECTED after rejection")
                .isEqualTo(TournamentStatus.REJECTED);
    }

    /**
     * Property 8 (exhaustive invalid transitions): all non-PENDING_APPROVAL statuses
     * are tested for both approve and reject operations in a single property.
     *
     * Validates: Requirement 6.5
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 8: All invalid transitions throw TournamentStateException")
    void allInvalidTransitionsThrowTournamentStateException(
            @ForAll("nonPendingStatusProvider") TournamentStatus invalidStatus,
            @ForAll @Positive Long tournamentId) {

        TournamentRepository tournamentRepository = Mockito.mock(TournamentRepository.class);
        TournamentMatchupRepository tournamentMatchupRepository = Mockito.mock(TournamentMatchupRepository.class);
        MemeRepository memeRepository = Mockito.mock(MemeRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        TournamentServiceImpl service = new TournamentServiceImpl(
                tournamentRepository,
                tournamentMatchupRepository,
                memeRepository,
                userRepository);

        Tournament tournament = buildTournamentStub(tournamentId, invalidStatus);

        when(tournamentRepository.findById(tournamentId)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        // Both approve and reject must throw for any non-PENDING_APPROVAL status
        assertThatThrownBy(() -> service.approveTournament(tournamentId))
                .as("approveTournament on %s must throw TournamentStateException", invalidStatus)
                .isInstanceOf(TournamentStateException.class);

        assertThatThrownBy(() -> service.rejectTournament(tournamentId))
                .as("rejectTournament on %s must throw TournamentStateException", invalidStatus)
                .isInstanceOf(TournamentStateException.class);
    }

    // -------------------------------------------------------------------------
    // Arbitraries / Providers
    // -------------------------------------------------------------------------

    /**
     * Provides all TournamentStatus values that are NOT PENDING_APPROVAL.
     * These are the statuses from which approve/reject must be rejected.
     */
    @Provide
    Arbitrary<TournamentStatus> nonPendingStatusProvider() {
        return Arbitraries.of(
                TournamentStatus.ACTIVE,
                TournamentStatus.COMPLETED,
                TournamentStatus.REJECTED
        );
    }

    /**
     * Provides the two admin operations: "approve" and "reject".
     */
    @Provide
    Arbitrary<String> operationProvider() {
        return Arbitraries.of("approve", "reject");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal Tournament stub with the given ID and status.
     * The creator is set to a stub User so that toTournamentResponse() can
     * call creator.getUsername() without a NullPointerException.
     */
    private Tournament buildTournamentStub(Long id, TournamentStatus status) {
        User creator = new User();
        creator.setId(1L);
        creator.setUsername("test_creator");

        Tournament tournament = new Tournament();
        tournament.setId(id);
        tournament.setName("Test Tournament");
        tournament.setCreator(creator);
        tournament.setStatus(status);
        tournament.setRoundDurationHours(1);
        tournament.setMemeCount(8);
        return tournament;
    }
}
