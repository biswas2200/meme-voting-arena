package com.meme.gdg.service;

import com.meme.gdg.dto.BattlePairResponse;
import com.meme.gdg.exception.InsufficientMemesException;
import com.meme.gdg.model.BattlePair;
import com.meme.gdg.model.Meme;
import com.meme.gdg.repository.BattlePairRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuickBattleServiceImpl.getNewPair().
 */
@ExtendWith(MockitoExtension.class)
class QuickBattleServiceUnitTest {

    @Mock private MemeService memeService;
    @Mock private BattlePairRepository battlePairRepository;

    @InjectMocks
    private QuickBattleServiceImpl quickBattleService;

    private Meme memeA;
    private Meme memeB;

    @BeforeEach
    void setUp() {
        memeA = new Meme();
        memeA.setId(1L);
        memeA.setTitle("Meme A");
        memeA.setImageUrl("https://example.com/a.jpg");
        memeA.setVoteCount(5);

        memeB = new Meme();
        memeB.setId(2L);
        memeB.setTitle("Meme B");
        memeB.setImageUrl("https://example.com/b.jpg");
        memeB.setVoteCount(3);
    }

    @Test
    void getNewPair_returnsBattlePairResponse_withCorrectPairId() {
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA, memeB));
        BattlePair saved = new BattlePair();
        saved.setId(42L);
        saved.setMemeA(memeA);
        saved.setMemeB(memeB);
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(saved);

        BattlePairResponse response = quickBattleService.getNewPair();

        assertThat(response.getPairId()).isEqualTo(42L);
    }

    @Test
    void getNewPair_memeASnapshot_hasCorrectIdTitleAndVoteCount() {
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA, memeB));
        BattlePair saved = new BattlePair();
        saved.setId(1L);
        saved.setMemeA(memeA);
        saved.setMemeB(memeB);
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(saved);

        BattlePairResponse response = quickBattleService.getNewPair();

        assertThat(response.getMemeA().getId()).isEqualTo(1L);
        assertThat(response.getMemeA().getTitle()).isEqualTo("Meme A");
        assertThat(response.getMemeA().getVoteCount()).isEqualTo(5);
        assertThat(response.getMemeA().getImageUrl()).isEqualTo("https://example.com/a.jpg");
    }

    @Test
    void getNewPair_memeBSnapshot_hasCorrectIdTitleAndVoteCount() {
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA, memeB));
        BattlePair saved = new BattlePair();
        saved.setId(1L);
        saved.setMemeA(memeA);
        saved.setMemeB(memeB);
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(saved);

        BattlePairResponse response = quickBattleService.getNewPair();

        assertThat(response.getMemeB().getId()).isEqualTo(2L);
        assertThat(response.getMemeB().getTitle()).isEqualTo("Meme B");
        assertThat(response.getMemeB().getVoteCount()).isEqualTo(3);
    }

    @Test
    void getNewPair_twoMemesHaveDistinctIds() {
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA, memeB));
        BattlePair saved = new BattlePair();
        saved.setId(1L);
        saved.setMemeA(memeA);
        saved.setMemeB(memeB);
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(saved);

        BattlePairResponse response = quickBattleService.getNewPair();

        assertThat(response.getMemeA().getId()).isNotEqualTo(response.getMemeB().getId());
    }

    @Test
    void getNewPair_memeServiceThrows_wrapsInInsufficientMemesException() {
        when(memeService.getTwoRandomMemes()).thenThrow(new RuntimeException("Not enough memes"));

        assertThatThrownBy(() -> quickBattleService.getNewPair())
                .isInstanceOf(InsufficientMemesException.class)
                .hasMessageContaining("Insufficient memes");
    }

    @Test
    void getNewPair_memeServiceReturnsNull_throwsInsufficientMemesException() {
        when(memeService.getTwoRandomMemes()).thenReturn(null);

        assertThatThrownBy(() -> quickBattleService.getNewPair())
                .isInstanceOf(InsufficientMemesException.class);
    }

    @Test
    void getNewPair_memeServiceReturnsOnlyOneMeme_throwsInsufficientMemesException() {
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA));

        assertThatThrownBy(() -> quickBattleService.getNewPair())
                .isInstanceOf(InsufficientMemesException.class);
    }

    @Test
    void getNewPair_memeWithNullVoteCount_defaultsToZero() {
        memeA.setVoteCount(null);
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA, memeB));
        BattlePair saved = new BattlePair();
        saved.setId(1L);
        saved.setMemeA(memeA);
        saved.setMemeB(memeB);
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(saved);

        BattlePairResponse response = quickBattleService.getNewPair();

        assertThat(response.getMemeA().getVoteCount()).isEqualTo(0);
    }

    @Test
    void getNewPair_persistsBattlePairToRepository() {
        when(memeService.getTwoRandomMemes()).thenReturn(List.of(memeA, memeB));
        BattlePair saved = new BattlePair();
        saved.setId(1L);
        saved.setMemeA(memeA);
        saved.setMemeB(memeB);
        when(battlePairRepository.save(any(BattlePair.class))).thenReturn(saved);

        quickBattleService.getNewPair();

        verify(battlePairRepository, times(1)).save(any(BattlePair.class));
    }
}
