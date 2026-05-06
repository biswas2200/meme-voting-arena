package com.meme.gdg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "battle_votes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "battle_pair_id"}),
    @UniqueConstraint(columnNames = {"user_id", "matchup_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BattleVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Exactly one of battle_pair_id or matchup_id is non-null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_pair_id")
    private BattlePair battlePair;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchup_id")
    private TournamentMatchup matchup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chosen_meme_id", nullable = false)
    private Meme chosenMeme;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
