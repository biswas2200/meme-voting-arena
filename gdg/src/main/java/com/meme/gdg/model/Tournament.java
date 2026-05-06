package com.meme.gdg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournaments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentStatus status;

    /** Duration of each round in hours. Valid values: 1, 6, or 24. */
    @Column(name = "round_duration_hours", nullable = false)
    private int roundDurationHours;

    /** Number of memes in the tournament. Valid values: 8 or 16. */
    @Column(name = "meme_count", nullable = false)
    private int memeCount;

    /** Current active round number (1-indexed). Null until tournament becomes ACTIVE. */
    @Column(name = "current_round")
    private Integer currentRound;

    /** Timestamp when the current round expires. */
    @Column(name = "current_round_ends_at")
    private LocalDateTime currentRoundEndsAt;

    /** The winning meme. Set when tournament status transitions to COMPLETED. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "champion_id")
    private Meme champion;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TournamentMatchup> matchups = new ArrayList<>();
}
