package com.meme.gdg.service;

import com.meme.gdg.model.Meme;
import com.meme.gdg.model.Vote;
import com.meme.gdg.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KeywordService — the "personality keyword" generator
 * derived from a user's recent upvote history.
 */
@ExtendWith(MockitoExtension.class)
class KeywordServiceUnitTest {

    private static final List<String> KNOWN_RANDOM_KEYWORDS = List.of(
            "funny", "hilarious", "epic", "legendary", "amazing", "awesome",
            "dank", "fresh", "spicy", "fire", "viral", "trending",
            "meme lord", "comedy gold", "internet famous", "laugh out loud",
            "wholesome", "savage", "relatable", "mood", "vibe", "energy"
    );

    @Mock
    private VoteRepository voteRepository;

    @InjectMocks
    private KeywordService keywordService;

    private Vote voteFor(String memeTitle) {
        Meme meme = new Meme();
        meme.setTitle(memeTitle);
        Vote vote = new Vote();
        vote.setMeme(meme);
        vote.setVoteType(Vote.VoteType.UPVOTE);
        return vote;
    }

    @Test
    void generateKeyword_noUpvoteHistory_returnsOneOfTheRandomKeywords() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(Collections.emptyList());

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(KNOWN_RANDOM_KEYWORDS).contains(keyword);
    }

    @Test
    void generateKeyword_recentUpvotesMentionCat_returnsAnimalLover() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(voteFor("Funny Cat Meme")));

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(keyword).isEqualTo("Animal Lover");
    }

    @Test
    void generateKeyword_recentUpvotesMentionDog_returnsAnimalLover() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(voteFor("My Dog Being Silly")));

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(keyword).isEqualTo("Animal Lover");
    }

    @Test
    void generateKeyword_recentUpvotesMentionCoding_returnsCodeWarrior() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(voteFor("Coding at 3am")));

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(keyword).isEqualTo("Code Warrior");
    }

    @Test
    void generateKeyword_recentUpvotesMentionFunny_returnsComedyConnoisseur() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(voteFor("This is so funny")));

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(keyword).isEqualTo("Comedy Connoisseur");
    }

    @Test
    void generateKeyword_recentUpvotesMentionSavage_returnsDarkHumorExpert() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(voteFor("That's savage")));

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(keyword).isEqualTo("Dark Humor Expert");
    }

    @Test
    void generateKeyword_noMatchingTheme_returnsOneOfTheRandomKeywords() {
        when(voteRepository.findTop5ByUserIdAndVoteTypeOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(voteFor("Random Unrelated Title")));

        String keyword = keywordService.generateKeywordForUser(1L);

        assertThat(KNOWN_RANDOM_KEYWORDS).contains(keyword);
    }
}
