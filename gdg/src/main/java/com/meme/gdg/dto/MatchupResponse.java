package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchupResponse {
    private Long id;
    private int roundNumber;
    private int bracketPosition;
    private MemeSnapshot memeA;
    private MemeSnapshot memeB;
    private int votesA;
    private int votesB;
    private MemeSnapshot winner; // nullable — null until round ends
}
