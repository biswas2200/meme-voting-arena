package com.meme.gdg.repository;

import com.meme.gdg.model.TournamentMatchup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentMatchupRepository extends JpaRepository<TournamentMatchup, Long> {

    List<TournamentMatchup> findByTournamentIdAndRoundNumber(Long tournamentId, int roundNumber);

    List<TournamentMatchup> findByTournamentId(Long tournamentId);
}
