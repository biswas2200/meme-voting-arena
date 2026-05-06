package com.meme.gdg.service;

import com.meme.gdg.dto.TournamentCreateRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TournamentServiceImpl — createTournament, approveTournament,
 * rejectTournament, getTournament, listTournaments, getMyTournaments.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TournamentServiceUnitTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentMatchupRepository tournamentMatchupRepository;
    @Mock private MemeRepository memeRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TournamentServiceImpl tournamentService;

    private User creator;
    private List<Meme> eightMemes;
    private AtomicLong idSeq;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setUsername("creator");

        eightMemes = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            Meme m = new Meme();
            m.setId(i);
            m.setTitle("Meme " + i);
            m.setImageUrl("https://example.com/meme" + i + ".jpg");
            m.setVoteCount(0);
            eightMemes.add(m);
        }

        idSeq = new AtomicLong(100L);

        // Default stubs used by most tests
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(memeRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return eightMemes.stream().filter(m -> m.getId().equals(id)).findFirst();
        });
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> {
            Tournament t = inv.getArgument(0);
            if (t.getId() == null) t.setId(idSeq.getAndIncrement());
            return t;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createTournament — happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createTournament_with8Memes_creates4FirstRoundMatchups() {
        TournamentCreateRequest req = buildRequest("Summer Showdown", eightMemes, 1);

        TournamentResponse response = tournamentService.createTournament(1L, req);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Summer Showdown");
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getMatchups()).hasSize(4);
        assertThat(response.getCurrentRound()).isNull();
    }

    @Test
    void createTournament_with16Memes_creates8FirstRoundMatchups() {
        List<Meme> sixteenMemes = new ArrayList<>();
        for (long i = 1; i <= 16; i++) {
            Meme m = new Meme();
            m.setId(i);
            m.setTitle("Meme " + i);
            m.setImageUrl("https://example.com/meme" + i + ".jpg");
            m.setVoteCount(0);
            sixteenMemes.add(m);
        }
        when(memeRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return sixteenMemes.stream().filter(m -> m.getId().equals(id)).findFirst();
        });

        TournamentCreateRequest req = buildRequest("Big Tournament", sixteenMemes, 6);

        TournamentResponse response = tournamentService.createTournament(1L, req);

        assertThat(response.getMatchups()).hasSize(8);
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void createTournament_bracketPositions_are1IndexedAndContiguous() {
        TournamentCreateRequest req = buildRequest("Bracket Test", eightMemes, 1);

        TournamentResponse response = tournamentService.createTournament(1L, req);

        List<Integer> positions = response.getMatchups().stream()
                .map(m -> m.getBracketPosition())
                .sorted()
                .collect(Collectors.toList());

        assertThat(positions).containsExactly(1, 2, 3, 4);
    }

    @Test
    void createTournament_allMemesAppearExactlyOnce_inFirstRound() {
        TournamentCreateRequest req = buildRequest("Unique Memes", eightMemes, 1);

        TournamentResponse response = tournamentService.createTournament(1L, req);

        List<Long> allMemeIds = response.getMatchups().stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getMemeA().getId(), m.getMemeB().getId()))
                .collect(Collectors.toList());

        assertThat(allMemeIds).hasSize(8);
        assertThat(allMemeIds.stream().distinct().count()).isEqualTo(8);
    }

    @Test
    void createTournament_creatorUsername_isCorrectInResponse() {
        TournamentCreateRequest req = buildRequest("Creator Test", eightMemes, 24);

        TournamentResponse response = tournamentService.createTournament(1L, req);

        assertThat(response.getCreator()).isEqualTo("creator");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createTournament — validation failures
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createTournament_with7Memes_throwsTournamentStateException() {
        List<Meme> sevenMemes = eightMemes.subList(0, 7);
        TournamentCreateRequest req = buildRequest("Bad Count", sevenMemes, 1);

        assertThatThrownBy(() -> tournamentService.createTournament(1L, req))
                .isInstanceOf(TournamentStateException.class)
                .hasMessageContaining("8 or 16");
    }

    @Test
    void createTournament_withDuplicateMemeIds_throwsTournamentStateException() {
        List<Long> duplicateIds = List.of(1L, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        TournamentCreateRequest req = new TournamentCreateRequest();
        req.setName("Duplicate Test");
        req.setMemeIds(duplicateIds);
        req.setRoundDurationHours(1);

        assertThatThrownBy(() -> tournamentService.createTournament(1L, req))
                .isInstanceOf(TournamentStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void createTournament_withInvalidRoundDuration_throwsTournamentStateException() {
        TournamentCreateRequest req = buildRequest("Bad Duration", eightMemes, 12);

        assertThatThrownBy(() -> tournamentService.createTournament(1L, req))
                .isInstanceOf(TournamentStateException.class)
                .hasMessageContaining("1, 6, or 24");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approveTournament
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void approveTournament_pendingTournament_transitionsToActive() {
        Tournament t = buildTournamentStub(50L, TournamentStatus.PENDING_APPROVAL);
        when(tournamentRepository.findById(50L)).thenReturn(Optional.of(t));

        TournamentResponse response = tournamentService.approveTournament(50L);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCurrentRound()).isEqualTo(1);
        assertThat(response.getCurrentRoundEndsAt()).isNotNull();
    }

    @Test
    void approveTournament_activeTournament_throwsTournamentStateException() {
        Tournament t = buildTournamentStub(51L, TournamentStatus.ACTIVE);
        when(tournamentRepository.findById(51L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> tournamentService.approveTournament(51L))
                .isInstanceOf(TournamentStateException.class);
    }

    @Test
    void approveTournament_completedTournament_throwsTournamentStateException() {
        Tournament t = buildTournamentStub(52L, TournamentStatus.COMPLETED);
        when(tournamentRepository.findById(52L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> tournamentService.approveTournament(52L))
                .isInstanceOf(TournamentStateException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rejectTournament
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void rejectTournament_pendingTournament_transitionsToRejected() {
        Tournament t = buildTournamentStub(60L, TournamentStatus.PENDING_APPROVAL);
        when(tournamentRepository.findById(60L)).thenReturn(Optional.of(t));

        TournamentResponse response = tournamentService.rejectTournament(60L);

        assertThat(response.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void rejectTournament_activeTournament_throwsTournamentStateException() {
        Tournament t = buildTournamentStub(61L, TournamentStatus.ACTIVE);
        when(tournamentRepository.findById(61L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> tournamentService.rejectTournament(61L))
                .isInstanceOf(TournamentStateException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTournament
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getTournament_existingId_returnsCorrectResponse() {
        Tournament t = buildTournamentStub(70L, TournamentStatus.ACTIVE);
        t.setCurrentRound(2);
        when(tournamentRepository.findById(70L)).thenReturn(Optional.of(t));

        TournamentResponse response = tournamentService.getTournament(70L);

        assertThat(response.getId()).isEqualTo(70L);
        assertThat(response.getName()).isEqualTo("Test Tournament");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getCurrentRound()).isEqualTo(2);
    }

    @Test
    void getTournament_nonExistingId_throwsRuntimeException() {
        when(tournamentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tournamentService.getTournament(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tournament not found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listTournaments
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void listTournaments_returnsPagedSummaries() {
        Tournament t1 = buildTournamentStub(80L, TournamentStatus.ACTIVE);
        Tournament t2 = buildTournamentStub(81L, TournamentStatus.PENDING_APPROVAL);
        Page<Tournament> page = new PageImpl<>(List.of(t1, t2));
        when(tournamentRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<?> result = tournamentService.listTournaments(Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMyTournaments
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getMyTournaments_returnsOnlyTournamentsCreatedByUser() {
        Tournament t = buildTournamentStub(90L, TournamentStatus.PENDING_APPROVAL);
        when(tournamentRepository.findByCreatorId(1L)).thenReturn(List.of(t));

        List<?> result = tournamentService.getMyTournaments(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getMyTournaments_noTournaments_returnsEmptyList() {
        when(tournamentRepository.findByCreatorId(1L)).thenReturn(List.of());

        List<?> result = tournamentService.getMyTournaments(1L);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private TournamentCreateRequest buildRequest(String name, List<Meme> memes, int durationHours) {
        TournamentCreateRequest req = new TournamentCreateRequest();
        req.setName(name);
        req.setMemeIds(memes.stream().map(Meme::getId).collect(Collectors.toList()));
        req.setRoundDurationHours(durationHours);
        return req;
    }

    private Tournament buildTournamentStub(Long id, TournamentStatus status) {
        Tournament t = new Tournament();
        t.setId(id);
        t.setName("Test Tournament");
        t.setCreator(creator);
        t.setStatus(status);
        t.setRoundDurationHours(1);
        t.setMemeCount(8);
        return t;
    }
}
