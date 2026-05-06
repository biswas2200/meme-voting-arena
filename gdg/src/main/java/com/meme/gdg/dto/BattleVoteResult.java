package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BattleVoteResult {
    // One of these will be set depending on context (quick battle vs tournament)
    private Long pairId;
    private Long matchupId;

    private int memeAVotes;
    private int memeBVotes;
    private Long chosenMemeId;
}
