package com.meme.gdg.service;

import com.meme.gdg.dto.BattlePairResponse;
import com.meme.gdg.model.BattlePair;
import com.meme.gdg.model.Meme;
import com.meme.gdg.repository.BattlePairRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for QuickBattleService.getNewPair().
 *
 * Validates: Requirements 1.1, 1.3
 */
public class QuickBattleServicePropertyTest {

    private MemeService memeService;
    private BattlePairRepository battlePairRepository;
    private QuickBattleServiceImpl quickBattleService;

    /**
     * Property 1: Quick Battle pair contains exactly two distinct memes.
     *
     * For any call to QuickBattleService.getNewPair() when the gallery contains
     * at least 2 memes, the returned BattlePairResponse SHALL contain exactly 2
     * memes with different IDs.
     *
     * Validates: Requirements 1.1, 1.3
     */
    @Property(tries = 100)
    @Label("Feature: battle-arena, Property 1: Quick Battle pair contains exactly two distinct memes")
    void quickBattlePairContainsTwoDistinctMemes(
            @ForAll @Size(min = 2, max = 20) List<@Positive Long> memeIds) {

        // Set up fresh mocks for each property try
        memeService = Mockito.mock(MemeService.class);
        battlePairRepository = Mockito.mock(BattlePairRepository.class);
        quickBattleService = new QuickBattleServiceImpl(memeService, battlePairRepository);

        // Deduplicate IDs to ensure we have at least 2 distinct ones
        List<Long> distinctIds = memeIds.stream()
                .distinct()
                .collect(Collectors.toList());

        // If deduplication left fewer than 2, skip this sample
        Assume.that(distinctIds.size() >= 2);

        // Build Meme stubs from the distinct IDs
        List<Meme> memes = new ArrayList<>();
        for (Long id : distinctIds) {
            Meme meme = new Meme();
            meme.setId(id);
            meme.setTitle("Meme " + id);
            meme.setImageUrl("http://example.com/meme/" + id + ".jpg");
            meme.setVoteCount(0);
            memes.add(meme);
        }

        // Shuffle and pick the first 2 to simulate getTwoRandomMemes() behavior
        Collections.shuffle(memes);
        List<Meme> twoMemes = List.of(memes.get(0), memes.get(1));

        // Stub memeService.getTwoRandomMemes() to return those 2 memes
        when(memeService.getTwoRandomMemes()).thenReturn(twoMemes);

        // Stub battlePairRepository.save() to return a BattlePair with id = 1L
        BattlePair savedPair = new BattlePair();
        savedPair.setId(1L);
        savedPair.setMemeA(twoMemes.get(0));
        savedPair.setMemeB(twoMemes.get(1));
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(savedPair);

        // Call the service
        BattlePairResponse response = quickBattleService.getNewPair();

        // Assert the pair ID is not null
        assertThat(response.getPairId()).isNotNull();

        // Assert the two memes have distinct IDs
        assertThat(response.getMemeA().getId())
                .as("memeA and memeB must have different IDs")
                .isNotEqualTo(response.getMemeB().getId());
    }
}
