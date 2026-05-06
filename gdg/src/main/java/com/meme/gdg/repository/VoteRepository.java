package com.meme.gdg.repository;

import com.meme.gdg.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {
    
    // Find vote by user and meme
    Optional<Vote> findByUserIdAndMemeId(Long userId, Long memeId);
    
    // Check if user has voted on a meme
    boolean existsByUserIdAndMemeId(Long userId, Long memeId);
    
    // Get user's voting history (last 5 upvoted memes)
    @Query("SELECT v FROM Vote v WHERE v.user.id = :userId AND v.voteType = 'UPVOTE' ORDER BY v.createdAt DESC")
    List<Vote> findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    // Get all votes for a meme
    List<Vote> findByMemeId(Long memeId);
    
    // Count upvotes for a meme
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.meme.id = :memeId AND v.voteType = 'UPVOTE'")
    long countUpvotesByMemeId(@Param("memeId") Long memeId);
    
    // Count downvotes for a meme
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.meme.id = :memeId AND v.voteType = 'DOWNVOTE'")
    long countDownvotesByMemeId(@Param("memeId") Long memeId);
}
