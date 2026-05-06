package com.meme.gdg.repository;

import com.meme.gdg.model.BattleVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BattleVoteRepository extends JpaRepository<BattleVote, Long> {

    Optional<BattleVote> findByUserIdAndBattlePairId(Long userId, Long pairId);

    Optional<BattleVote> findByUserIdAndMatchupId(Long userId, Long matchupId);

    int countByMatchupIdAndChosenMemeId(Long matchupId, Long memeId);

    int countByBattlePairIdAndChosenMemeId(Long battlePairId, Long memeId);

    List<BattleVote> findByMatchupId(Long matchupId);
}
