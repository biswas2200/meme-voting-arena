package com.meme.gdg.service;

import com.meme.gdg.dto.MemeRequest;
import com.meme.gdg.dto.MemeResponse;
import com.meme.gdg.dto.VoteRequest;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.User;
import com.meme.gdg.model.Vote;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MemeService — covers all public methods with concrete assertion values.
 */
@ExtendWith(MockitoExtension.class)
class MemeServiceUnitTest {

    @Mock
    private MemeRepository memeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MemeService memeService;

    private User testUser;
    private Meme meme1;
    private Meme meme2;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encoded_password");
        testUser.setRole(User.Role.USER);

        meme1 = new Meme();
        meme1.setId(10L);
        meme1.setTitle("Funny Cat");
        meme1.setImageUrl("https://example.com/cat.jpg");
        meme1.setUploadedBy(testUser);
        meme1.setVoteCount(42);

        meme2 = new Meme();
        meme2.setId(20L);
        meme2.setTitle("Doge Meme");
        meme2.setImageUrl("https://example.com/doge.jpg");
        meme2.setUploadedBy(testUser);
        meme2.setVoteCount(15);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllMemes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getAllMemes_returnsPagedResults_withCorrectVoteCount() {
        Page<Meme> page = new PageImpl<>(List.of(meme1, meme2));
        when(memeRepository.findAllByOrderByVoteCountDesc(any(Pageable.class))).thenReturn(page);

        Page<MemeResponse> result = memeService.getAllMemes(0, 12, null);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Funny Cat");
        assertThat(result.getContent().get(0).getVoteCount()).isEqualTo(42);
        assertThat(result.getContent().get(1).getId()).isEqualTo(20L);
        assertThat(result.getContent().get(1).getVoteCount()).isEqualTo(15);
    }

    @Test
    void getAllMemes_emptyGallery_returnsEmptyPage() {
        when(memeRepository.findAllByOrderByVoteCountDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<MemeResponse> result = memeService.getAllMemes(0, 12, null);

        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getLeaderboard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getLeaderboard_returnsTop5Memes_orderedByVoteCount() {
        when(memeRepository.findTop5ByOrderByVoteCountDesc()).thenReturn(List.of(meme1, meme2));

        List<MemeResponse> result = memeService.getLeaderboard(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getVoteCount()).isEqualTo(42);
        assertThat(result.get(1).getId()).isEqualTo(20L);
        assertThat(result.get(1).getVoteCount()).isEqualTo(15);
    }

    @Test
    void getLeaderboard_emptyGallery_returnsEmptyList() {
        when(memeRepository.findTop5ByOrderByVoteCountDesc()).thenReturn(Collections.emptyList());

        List<MemeResponse> result = memeService.getLeaderboard(null);

        assertThat(result).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createMeme
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createMeme_withNoAuth_throwsAuthenticationRequired() {
        MemeRequest request = new MemeRequest();
        request.setTitle("New Meme");
        request.setImageUrl("https://example.com/new.jpg");

        assertThatThrownBy(() -> memeService.createMeme(request, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void createMeme_withValidAuth_returnsSavedMeme() {
        MemeRequest request = new MemeRequest();
        request.setTitle("New Meme");
        request.setImageUrl("https://example.com/new.jpg");

        com.meme.gdg.security.UserPrincipal principal = com.meme.gdg.security.UserPrincipal.create(testUser);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(memeRepository.save(any(Meme.class))).thenAnswer(inv -> {
            Meme m = inv.getArgument(0);
            m.setId(99L);
            return m;
        });

        MemeResponse result = memeService.createMeme(request, auth);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getTitle()).isEqualTo("New Meme");
        assertThat(result.getImageUrl()).isEqualTo("https://example.com/new.jpg");
        assertThat(result.getVoteCount()).isEqualTo(0);
        assertThat(result.getUploadedBy()).isEqualTo("testuser");
    }

    @Test
    void createMeme_noUsersInDb_throwsRuntimeException() {
        MemeRequest request = new MemeRequest();
        request.setTitle("Orphan Meme");
        request.setImageUrl("https://example.com/orphan.jpg");

        com.meme.gdg.security.UserPrincipal principal = com.meme.gdg.security.UserPrincipal.create(testUser);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memeService.createMeme(request, auth))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // voteMeme
    // ─────────────────────────────────────────────────────────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken buildAuth() {
        com.meme.gdg.security.UserPrincipal principal = com.meme.gdg.security.UserPrincipal.create(testUser);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    @Test
    void voteMeme_withNoAuth_throwsAuthenticationRequired() {
        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType(Vote.VoteType.UPVOTE);

        assertThatThrownBy(() -> memeService.voteMeme(10L, voteRequest, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void voteMeme_upvote_incrementsVoteCountByOne() {
        meme1.setVoteCount(10);
        when(memeRepository.findById(10L)).thenReturn(Optional.of(meme1));
        when(memeRepository.save(any(Meme.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        when(voteRepository.findByUserIdAndMemeId(anyLong(), anyLong())).thenReturn(Optional.empty());

        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType(Vote.VoteType.UPVOTE);

        MemeResponse result = memeService.voteMeme(10L, voteRequest, buildAuth());

        assertThat(result.getVoteCount()).isEqualTo(11);
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void voteMeme_downvote_decrementsVoteCountByOne() {
        meme1.setVoteCount(10);
        when(memeRepository.findById(10L)).thenReturn(Optional.of(meme1));
        when(memeRepository.save(any(Meme.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        when(voteRepository.findByUserIdAndMemeId(anyLong(), anyLong())).thenReturn(Optional.empty());

        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType(Vote.VoteType.DOWNVOTE);

        MemeResponse result = memeService.voteMeme(10L, voteRequest, buildAuth());

        assertThat(result.getVoteCount()).isEqualTo(9);
    }

    @Test
    void voteMeme_memeNotFound_throwsRuntimeException() {
        when(memeRepository.findById(999L)).thenReturn(Optional.empty());

        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType(Vote.VoteType.UPVOTE);

        assertThatThrownBy(() -> memeService.voteMeme(999L, voteRequest, buildAuth()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Meme not found");
    }

    @Test
    void voteMeme_broadcastsWebSocketMessage() {
        meme1.setVoteCount(5);
        when(memeRepository.findById(10L)).thenReturn(Optional.of(meme1));
        when(memeRepository.save(any(Meme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteRepository.findByUserIdAndMemeId(anyLong(), anyLong())).thenReturn(Optional.empty());

        VoteRequest voteRequest = new VoteRequest();
        voteRequest.setVoteType(Vote.VoteType.UPVOTE);

        memeService.voteMeme(10L, voteRequest, buildAuth());

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/votes"), any(MemeResponse.class));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getTwoRandomMemes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getTwoRandomMemes_withAtLeastTwoMemes_returnsTwoDistinctMemes() {
        when(memeRepository.count()).thenReturn(2L);
        when(memeRepository.findAllOrderById(any(Pageable.class))).thenReturn(List.of(meme1, meme2));

        List<Meme> result = memeService.getTwoRandomMemes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isNotEqualTo(result.get(1).getId());
    }

    @Test
    void getTwoRandomMemes_withOnlyOneMeme_throwsRuntimeException() {
        when(memeRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> memeService.getTwoRandomMemes())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not enough memes");
    }

    @Test
    void getTwoRandomMemes_withZeroMemes_throwsRuntimeException() {
        when(memeRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> memeService.getTwoRandomMemes())
                .isInstanceOf(RuntimeException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // convertToMemeResponse — indirect via getAllMemes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void memeResponse_uploadedByIsAnonymous_whenUploadedByIsNull() {
        Meme orphanMeme = new Meme();
        orphanMeme.setId(55L);
        orphanMeme.setTitle("Orphan");
        orphanMeme.setImageUrl("https://example.com/orphan.jpg");
        orphanMeme.setUploadedBy(null);
        orphanMeme.setVoteCount(0);

        Page<Meme> page = new PageImpl<>(List.of(orphanMeme));
        when(memeRepository.findAllByOrderByVoteCountDesc(any(Pageable.class))).thenReturn(page);

        Page<MemeResponse> result = memeService.getAllMemes(0, 12, null);

        assertThat(result.getContent().get(0).getUploadedBy()).isEqualTo("Anonymous");
    }

    @Test
    void memeResponse_userVotedIsFalse_whenNoAuthentication() {
        Page<Meme> page = new PageImpl<>(List.of(meme1));
        when(memeRepository.findAllByOrderByVoteCountDesc(any(Pageable.class))).thenReturn(page);

        Page<MemeResponse> result = memeService.getAllMemes(0, 12, null);

        assertThat(result.getContent().get(0).getUserVoted()).isFalse();
        assertThat(result.getContent().get(0).getUserVoteType()).isNull();
    }
}
