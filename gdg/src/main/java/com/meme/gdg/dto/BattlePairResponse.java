package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BattlePairResponse {
    private Long pairId;
    private MemeSnapshot memeA;
    private MemeSnapshot memeB;
}
