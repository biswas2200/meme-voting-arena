package com.meme.gdg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TournamentCreateRequest {

    @NotBlank(message = "Tournament name is required")
    @Size(min = 3, max = 100, message = "Tournament name must be between 3 and 100 characters")
    private String name;

    @NotEmpty(message = "Meme IDs are required")
    @Size(min = 8, max = 16, message = "Tournament requires between 8 and 16 memes")
    private List<Long> memeIds;

    @NotNull(message = "Round duration is required")
    private Integer roundDurationHours;
}
