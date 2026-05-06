package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentSummaryResponse {
    private Long id;
    private String name;
    private String creator; // username of the creator
    private String status;  // TournamentStatus enum name
    private int memeCount;
    private LocalDateTime createdAt;
}
