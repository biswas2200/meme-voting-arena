package com.meme.gdg.service;

import com.meme.gdg.dto.MatchupResponse;
import com.meme.gdg.dto.MemeSnapshot;
import com.meme.gdg.dto.TournamentCreateRequest;
import com.meme.gdg.dto.TournamentResponse;
import com.meme.gdg.dto.TournamentSummaryResponse;
import com.meme.gdg.exception.TournamentStateException;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.Tournament;
import com.meme.gdg.model.TournamentMatchup;
import com.meme.gdg.model.TournamentStatus;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.TournamentMatchupRepository;
import com.meme.gdg.repository.TournamentRepository;
import com.meme.gdg.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentMatchupRepository tournamentMatchupRepository;
    private final MemeRepository memeRepository;
    private final UserRepository userRepository;

    @Override
    public TournamentResponse createTournament(Long creatorId, TournamentCreateRequest request) {
        List<Long> memeIds = request.getMemeIds();

        // Validate meme count is exactly 8 or 16
        int memeCount = memeIds.size();
        if (memeCount != 8 && memeCount != 16) {
            throw new TournamentStateException("Tournament requires exactly 8 or 16 memes");
        }

        // Validate no duplicate meme IDs
        Set<Long> uniqueIds = new HashSet<>(memeIds);
        if (uniqueIds.size() != memeIds.size()) {
            throw new TournamentStateException("Duplicate meme IDs are not allowed");
        }

        // Validate all meme IDs exist
        List<Meme> memes = new ArrayList<>();
        for (Long memeId : memeIds) {
            Meme meme = memeRepository.findById(memeId)
                    .orElseThrow(() -> new RuntimeException("Meme not found: " + memeId));
            memes.add(meme);
        }

        // Validate roundDurationHours is 1, 6, or 24
        int roundDurationHours = request.getRoundDurationHours();
        if (roundDurationHours != 1 && roundDurationHours != 6 && roundDurationHours != 24) {
            throw new TournamentStateException("Round duration must be 1, 6, or 24 hours");
        }

        // Load creator
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found: " + creatorId));

        // Create Tournament entity
        Tournament tournament = Tournament.builder()
                .name(request.getName())
                .creator(creator)
                .status(TournamentStatus.PENDING_APPROVAL)
                .roundDurationHours(roundDurationHours)
                .memeCount(memeCount)
                .build();

        // Randomly shuffle memes and generate first-round matchups
        List<Meme> shuffled = new ArrayList<>(memes);
        Collections.shuffle(shuffled);

        List<TournamentMatchup> matchups = new ArrayList<>();
        for (int i = 0; i < shuffled.size(); i += 2) {
            int bracketPosition = (i / 2) + 1; // 1-indexed
            TournamentMatchup matchup = TournamentMatchup.builder()
                    .tournament(tournament)
                    .roundNumber(1)
                    .bracketPosition(bracketPosition)
                    .memeA(shuffled.get(i))
                    .memeB(shuffled.get(i + 1))
                    .build();
            matchups.add(matchup);
        }

        tournament.getMatchups().addAll(matchups);

        Tournament saved = tournamentRepository.save(tournament);
        log.info("Created tournament id={} name='{}' memeCount={} by userId={}",
                saved.getId(), saved.getName(), saved.getMemeCount(), creatorId);

        return toTournamentResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TournamentResponse getTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found: " + id));
        return toTournamentResponse(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TournamentSummaryResponse> listTournaments(Pageable pageable) {
        return tournamentRepository.findAll(pageable)
                .map(this::toTournamentSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentSummaryResponse> getMyTournaments(Long userId) {
        return tournamentRepository.findByCreatorId(userId).stream()
                .map(this::toTournamentSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentSummaryResponse> getPendingTournaments() {
        return tournamentRepository.findByStatus(TournamentStatus.PENDING_APPROVAL).stream()
                .map(this::toTournamentSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public TournamentResponse approveTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found: " + id));
        if (tournament.getStatus() != TournamentStatus.PENDING_APPROVAL) {
            throw new TournamentStateException("Tournament is not in PENDING_APPROVAL status");
        }
        tournament.setStatus(TournamentStatus.ACTIVE);
        tournament.setCurrentRound(1);
        tournament.setCurrentRoundEndsAt(
                java.time.LocalDateTime.now().plusHours(tournament.getRoundDurationHours()));
        return toTournamentResponse(tournamentRepository.save(tournament));
    }

    @Override
    public TournamentResponse rejectTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found: " + id));
        if (tournament.getStatus() != TournamentStatus.PENDING_APPROVAL) {
            throw new TournamentStateException("Tournament is not in PENDING_APPROVAL status");
        }
        tournament.setStatus(TournamentStatus.REJECTED);
        return toTournamentResponse(tournamentRepository.save(tournament));
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private TournamentResponse toTournamentResponse(Tournament tournament) {
        List<MatchupResponse> matchupResponses = tournament.getMatchups().stream()
                .map(this::toMatchupResponse)
                .collect(Collectors.toList());

        return new TournamentResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getCreator().getUsername(),
                tournament.getStatus().name(),
                tournament.getRoundDurationHours(),
                tournament.getCurrentRound(),
                tournament.getCurrentRoundEndsAt(),
                tournament.getChampion() != null ? toMemeSnapshot(tournament.getChampion()) : null,
                matchupResponses,
                tournament.getCreatedAt(),
                tournament.getCompletedAt()
        );
    }

    private TournamentSummaryResponse toTournamentSummaryResponse(Tournament tournament) {
        return new TournamentSummaryResponse(
                tournament.getId(),
                tournament.getName(),
                tournament.getCreator().getUsername(),
                tournament.getStatus().name(),
                tournament.getMemeCount(),
                tournament.getCreatedAt()
        );
    }

    private MatchupResponse toMatchupResponse(TournamentMatchup matchup) {
        return new MatchupResponse(
                matchup.getId(),
                matchup.getRoundNumber(),
                matchup.getBracketPosition(),
                toMemeSnapshot(matchup.getMemeA()),
                toMemeSnapshot(matchup.getMemeB()),
                matchup.getVotesA(),
                matchup.getVotesB(),
                matchup.getWinner() != null ? toMemeSnapshot(matchup.getWinner()) : null
        );
    }

    private MemeSnapshot toMemeSnapshot(Meme meme) {
        return new MemeSnapshot(
                meme.getId(),
                meme.getTitle(),
                meme.getImageUrl(),
                meme.getVoteCount() != null ? meme.getVoteCount() : 0
        );
    }
}
