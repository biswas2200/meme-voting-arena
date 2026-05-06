package com.meme.gdg.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single head-to-head matchup within a tournament bracket.
 * Full implementation is completed in task 1.4.
 */
@Entity
@Table(name = "tournament_matchups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentMatchup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "bracket_position", nullable = false)
    private int bracketPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_a_id", nullable = false)
    private Meme memeA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meme_b_id", nullable = false)
    private Meme memeB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Meme winner;

    @Builder.Default
    @Column(name = "votes_a")
    private int votesA = 0;

    @Builder.Default
    @Column(name = "votes_b")
    private int votesB = 0;

    @OneToMany(mappedBy = "matchup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BattleVote> votes = new ArrayList<>();
}
