package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemeResponse {
    private Long id;
    private String title;
    private String imageUrl;
    private String uploadedBy;
    private LocalDateTime uploadDate;
    private Integer voteCount;
    private Boolean userVoted;
    private String userVoteType;
}
