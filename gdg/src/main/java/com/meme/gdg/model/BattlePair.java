package com.meme.gdg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "battle_pairs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BattlePair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_a_id", nullable = false)
    private Meme memeA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_b_id", nullable = false)
    private Meme memeB;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
