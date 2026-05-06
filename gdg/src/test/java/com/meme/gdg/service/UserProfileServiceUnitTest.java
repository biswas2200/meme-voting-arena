package com.meme.gdg.service;

import com.meme.gdg.dto.UpdateProfileRequest;
import com.meme.gdg.dto.UserStatsResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserProfileService — getUserStats, updateProfile.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private MemeRepository memeRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private KeywordService keywordService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserProfileService userProfileService;

    private User testUser;
    private Meme testMeme;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alice");
        testUser.setEmail("alice@example.com");
        testUser.setPassword("encoded_pw");
        testUser.setRole(User.Role.USER);

        testMeme = new Meme();
        testMeme.setId(10L);
        testMeme.setTitle("Alice's Meme");
        testMeme.setImageUrl("https://example.com/alice.jpg");
        testMeme.setUploadedBy(testUser);
        testMeme.setVoteCount(7);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserStats
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getUserStats_returnsCorrectUsernameAndEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(memeRepository.findByUploadedByIdOrderByUploadDateDesc(1L)).thenReturn(List.of(testMeme));
        when(voteRepository.countUpvotesByMemeId(10L)).thenReturn(5L);
        when(voteRepository.countDownvotesByMemeId(10L)).thenReturn(2L);
        when(voteRepository.findAll()).thenReturn(Collections.emptyList());
        when(keywordService.generateKeywordForUser(1L)).thenReturn("funny-cat");

        UserStatsResponse result = userProfileService.getUserStats(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getRole()).isEqualTo("USER");
    }

    @Test
    void getUserStats_memesUploadedCount_isCorrect() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(memeRepository.findByUploadedByIdOrderByUploadDateDesc(1L)).thenReturn(List.of(testMeme));
        when(voteRepository.countUpvotesByMemeId(10L)).thenReturn(3L);
        when(voteRepository.countDownvotesByMemeId(10L)).thenReturn(1L);
        when(voteRepository.findAll()).thenReturn(Collections.emptyList());
        when(keywordService.generateKeywordForUser(1L)).thenReturn("keyword");

        UserStatsResponse result = userProfileService.getUserStats(1L);

        assertThat(result.getTotalMemesUploaded()).isEqualTo(1);
        assertThat(result.getTotalUpvotes()).isEqualTo(3);
        assertThat(result.getTotalDownvotes()).isEqualTo(1);
    }

    @Test
    void getUserStats_userNotFound_throwsRuntimeException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getUserStats(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getUserStats_noMemes_returnsZeroCounts() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(memeRepository.findByUploadedByIdOrderByUploadDateDesc(1L)).thenReturn(Collections.emptyList());
        when(voteRepository.findAll()).thenReturn(Collections.emptyList());
        when(keywordService.generateKeywordForUser(1L)).thenReturn("keyword");

        UserStatsResponse result = userProfileService.getUserStats(1L);

        assertThat(result.getTotalMemesUploaded()).isEqualTo(0);
        assertThat(result.getTotalUpvotes()).isEqualTo(0);
        assertThat(result.getTotalDownvotes()).isEqualTo(0);
        assertThat(result.getTopMemes()).isEmpty();
        assertThat(result.getRecentMemes()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateProfile — username
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateProfile_changesUsername_whenNewUsernameIsAvailable() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("alice_new");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("alice_new")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        // getUserStats is called at the end — stub it minimally
        when(memeRepository.findByUploadedByIdOrderByUploadDateDesc(1L)).thenReturn(Collections.emptyList());
        when(voteRepository.findAll()).thenReturn(Collections.emptyList());
        when(keywordService.generateKeywordForUser(1L)).thenReturn("kw");

        UserStatsResponse result = userProfileService.updateProfile(1L, req);

        assertThat(result.getUsername()).isEqualTo("alice_new");
        verify(userRepository).save(argThat(u -> "alice_new".equals(u.getUsername())));
    }

    @Test
    void updateProfile_throwsException_whenUsernameAlreadyTaken() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("bob");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateProfile(1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void updateProfile_throwsException_whenEmailAlreadyInUse() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("bob@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateProfile(1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void updateProfile_throwsException_whenCurrentPasswordMissing_forPasswordChange() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNewPassword("newSecret123");
        // currentPassword intentionally left null

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userProfileService.updateProfile(1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Current password is required");
    }

    @Test
    void updateProfile_throwsException_whenCurrentPasswordIsWrong() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("wrongPassword");
        req.setNewPassword("newSecret123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encoded_pw")).thenReturn(false);

        assertThatThrownBy(() -> userProfileService.updateProfile(1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void updateProfile_changesPassword_whenCurrentPasswordIsCorrect() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword("correctPassword");
        req.setNewPassword("newSecret123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "encoded_pw")).thenReturn(true);
        when(passwordEncoder.encode("newSecret123")).thenReturn("new_encoded_pw");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memeRepository.findByUploadedByIdOrderByUploadDateDesc(1L)).thenReturn(Collections.emptyList());
        when(voteRepository.findAll()).thenReturn(Collections.emptyList());
        when(keywordService.generateKeywordForUser(1L)).thenReturn("kw");

        userProfileService.updateProfile(1L, req);

        verify(userRepository).save(argThat(u -> "new_encoded_pw".equals(u.getPassword())));
    }
}
