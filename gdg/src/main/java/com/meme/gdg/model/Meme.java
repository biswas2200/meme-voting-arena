package com.meme.gdg.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "memes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Meme {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false, length = 500)
    private String imageUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;
    
    @CreationTimestamp
    @Column(name = "upload_date")
    private LocalDateTime uploadDate;
    
    @Column(name = "vote_count")
    private Integer voteCount = 0;
    
    @OneToMany(mappedBy = "meme", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Vote> votes = new ArrayList<>();
    
    // Helper method to calculate vote count
    public void updateVoteCount() {
        this.voteCount = votes.stream()
                .mapToInt(vote -> vote.getVoteType() == Vote.VoteType.UPVOTE ? 1 : -1)
                .sum();
    }
}
