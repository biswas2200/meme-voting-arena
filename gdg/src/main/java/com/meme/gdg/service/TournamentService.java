package com.meme.gdg.service;

import com.meme.gdg.dto.TournamentCreateRequest;
import com.meme.gdg.dto.TournamentResponse;
import com.meme.gdg.dto.TournamentSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TournamentService {

    TournamentResponse createTournament(Long creatorId, TournamentCreateRequest request);

    TournamentResponse getTournament(Long id);

    Page<TournamentSummaryResponse> listTournaments(Pageable pageable);

    List<TournamentSummaryResponse> getMyTournaments(Long userId);

    List<TournamentSummaryResponse> getPendingTournaments();

    TournamentResponse approveTournament(Long id);

    TournamentResponse rejectTournament(Long id);
}
