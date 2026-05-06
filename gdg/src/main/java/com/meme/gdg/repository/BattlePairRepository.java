package com.meme.gdg.repository;

import com.meme.gdg.model.BattlePair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BattlePairRepository extends JpaRepository<BattlePair, Long> {
}
