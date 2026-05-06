package com.meme.gdg.repository;

import com.meme.gdg.model.Tournament;
import com.meme.gdg.model.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findByStatus(TournamentStatus status);

    List<Tournament> findByStatusAndCurrentRoundEndsAtBefore(TournamentStatus status, LocalDateTime dateTime);

    List<Tournament> findByCreatorId(Long creatorId);
}
