package com.meme.gdg.dto;

import com.meme.gdg.model.Vote;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VoteRequest {
    @NotNull(message = "Vote type is required")
    private Vote.VoteType voteType;
}
