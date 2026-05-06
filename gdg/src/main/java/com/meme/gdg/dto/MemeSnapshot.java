package com.meme.gdg.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemeSnapshot {
    private Long id;
    private String title;
    private String imageUrl;
    private int voteCount;
}
