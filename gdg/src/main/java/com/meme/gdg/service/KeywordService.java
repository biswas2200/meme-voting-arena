package com.meme.gdg.service;

import com.meme.gdg.model.Vote;
import com.meme.gdg.repository.VoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeywordService {
    
    @Autowired
    private VoteRepository voteRepository;
    
    // Simple keyword generation based on meme titles
    private final List<String> memeKeywords = Arrays.asList(
        "funny", "hilarious", "epic", "legendary", "amazing", "awesome", 
        "dank", "fresh", "spicy", "fire", "viral", "trending",
        "meme lord", "comedy gold", "internet famous", "laugh out loud",
        "wholesome", "savage", "relatable", "mood", "vibe", "energy"
    );
    
    public String generateKeywordForUser(Long userId) {
        List<Vote> userUpvotes = voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(userId);
        
        if (userUpvotes.isEmpty()) {
            return getRandomKeyword();
        }
        
        // Simple AI-like keyword generation based on voting patterns
        // In a real application, this would use ML/NLP to analyze meme content
        List<String> memeTitle = userUpvotes.stream()
                .map(vote -> vote.getMeme().getTitle().toLowerCase())
                .collect(Collectors.toList());
        
        // Analyze common themes (very simplified)
        if (memeTitle.stream().anyMatch(title -> title.contains("cat") || title.contains("dog"))) {
            return "Animal Lover";
        } else if (memeTitle.stream().anyMatch(title -> title.contains("coding") || title.contains("programming"))) {
            return "Code Warrior";
        } else if (memeTitle.stream().anyMatch(title -> title.contains("funny") || title.contains("comedy"))) {
            return "Comedy Connoisseur";
        } else if (memeTitle.stream().anyMatch(title -> title.contains("dark") || title.contains("savage"))) {
            return "Dark Humor Expert";
        } else {
            return getRandomKeyword();
        }
    }
    
    private String getRandomKeyword() {
        Random random = new Random();
        return memeKeywords.get(random.nextInt(memeKeywords.size()));
    }
}
