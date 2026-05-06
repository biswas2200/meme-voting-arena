package com.meme.gdg.service;

import com.meme.gdg.dto.BattleVoteResult;

public interface BattleVoteService {
    BattleVoteResult voteOnPair(Long userId, Long battlePairId, Long chosenMemeId);
    BattleVoteResult voteOnMatchup(Long userId, Long matchupId, Long chosenMemeId);
}
