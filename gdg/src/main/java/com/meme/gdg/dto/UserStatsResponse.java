package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String keyword;
    private String avatarUrl;
    private LocalDateTime createdAt;

    // Stats
    private int totalMemesUploaded;
    private int totalVotesReceived;
    private int totalUpvotes;
    private int totalDownvotes;
    private int totalVotesCast;

    // Top memes
    private List<MemeResponse> topMemes;
    private List<MemeResponse> recentMemes;
}
