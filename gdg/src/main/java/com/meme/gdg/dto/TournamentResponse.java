package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentResponse {
    private Long id;
    private String name;
    private String creator; // username of the creator
    private String status;  // TournamentStatus enum name
    private int roundDurationHours;
    private Integer currentRound; // null until ACTIVE
    private LocalDateTime currentRoundEndsAt;
    private MemeSnapshot champion; // nullable — set when COMPLETED
    private List<MatchupResponse> matchups;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
