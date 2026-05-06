package com.meme.gdg.repository;

import com.meme.gdg.model.Meme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemeRepository extends JpaRepository<Meme, Long> {

    // Find memes ordered by vote count descending
    Page<Meme> findAllByOrderByVoteCountDesc(Pageable pageable);

    // Find top N memes for leaderboard
    List<Meme> findTop5ByOrderByVoteCountDesc();

    // Find memes by user
    List<Meme> findByUploadedByIdOrderByUploadDateDesc(Long userId);

    // Search memes by title (case-insensitive)
    @Query("SELECT m FROM Meme m WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%')) ORDER BY m.voteCount DESC")
    List<Meme> findByTitleContainingIgnoreCase(@Param("title") String title);

    // Fetch one meme by offset — used by the service layer to pick random memes
    // without any DB-level random function (works on H2, PostgreSQL, and any other DB)
    @Query("SELECT m FROM Meme m ORDER BY m.id ASC")
    List<Meme> findAllOrderById(Pageable pageable);
}
