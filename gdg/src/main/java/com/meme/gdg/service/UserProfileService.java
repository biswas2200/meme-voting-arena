package com.meme.gdg.service;

import com.meme.gdg.dto.MemeResponse;
import com.meme.gdg.dto.UpdateProfileRequest;
import com.meme.gdg.dto.UserStatsResponse;
import com.meme.gdg.model.Meme;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.repository.VoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemeRepository memeRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private KeywordService keywordService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Build the full stats response for a user */
    public UserStatsResponse getUserStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Memes uploaded by this user
        List<Meme> userMemes = memeRepository.findByUploadedByIdOrderByUploadDateDesc(userId);

        // Aggregate vote counts across all their memes
        int totalUpvotes = userMemes.stream()
                .mapToInt(m -> (int) voteRepository.countUpvotesByMemeId(m.getId()))
                .sum();
        int totalDownvotes = userMemes.stream()
                .mapToInt(m -> (int) voteRepository.countDownvotesByMemeId(m.getId()))
                .sum();

        // Votes this user has cast
        int totalVotesCast = (int) voteRepository.findAll().stream()
                .filter(v -> v.getUser().getId().equals(userId))
                .count();

        // Top 3 memes by vote count
        List<MemeResponse> topMemes = userMemes.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getVoteCount() == null ? 0 : b.getVoteCount(),
                        a.getVoteCount() == null ? 0 : a.getVoteCount()))
                .limit(3)
                .map(this::toMemeResponse)
                .collect(Collectors.toList());

        // 5 most recent memes
        List<MemeResponse> recentMemes = userMemes.stream()
                .limit(5)
                .map(this::toMemeResponse)
                .collect(Collectors.toList());

        String keyword = keywordService.generateKeywordForUser(userId);

        return new UserStatsResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                keyword,
                user.getAvatarUrl(),
                user.getCreatedAt(),
                userMemes.size(),
                totalUpvotes + totalDownvotes,
                totalUpvotes,
                totalDownvotes,
                totalVotesCast,
                topMemes,
                recentMemes
        );
    }

    /** Upload / replace profile avatar */
    public UserStatsResponse uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (file.isEmpty()) throw new RuntimeException("Please select an image file");

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            throw new RuntimeException("Only image files are allowed");

        if (file.getSize() > 2 * 1024 * 1024)
            throw new RuntimeException("Avatar must be under 2 MB");

        try {
            Path uploadDir = Paths.get("uploads/avatars");
            if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

            // Delete old avatar file if it was a local upload
            if (user.getAvatarUrl() != null && user.getAvatarUrl().startsWith("/uploads/")) {
                Path old = Paths.get(user.getAvatarUrl().substring(1));
                Files.deleteIfExists(old);
            }

            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains("."))
                ext = original.substring(original.lastIndexOf("."));

            String filename = "avatar_" + userId + "_" + UUID.randomUUID() + ext;
            Path dest = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), dest);

            user.setAvatarUrl("/uploads/avatars/" + filename);
            userRepository.save(user);
            log.info("Avatar updated for user id={}", userId);
            return getUserStats(userId);

        } catch (IOException e) {
            log.error("Avatar upload failed: {}", e.getMessage());
            throw new RuntimeException("Failed to upload avatar: " + e.getMessage());
        }
    }

    /** Update username / email / password */
    public UserStatsResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Username change
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            String newUsername = req.getUsername().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new RuntimeException("Username '" + newUsername + "' is already taken");
                }
                user.setUsername(newUsername);
            }
        }

        // Email change
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            String newEmail = req.getEmail().trim();
            if (!newEmail.equals(user.getEmail())) {
                if (userRepository.existsByEmail(newEmail)) {
                    throw new RuntimeException("Email '" + newEmail + "' is already in use");
                }
                user.setEmail(newEmail);
            }
        }

        // Password change — requires current password verification
        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getCurrentPassword() == null || req.getCurrentPassword().isBlank()) {
                throw new RuntimeException("Current password is required to set a new password");
            }
            if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
                throw new RuntimeException("Current password is incorrect");
            }
            user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        }

        userRepository.save(user);
        log.info("Profile updated for user id={}", userId);
        return getUserStats(userId);
    }

    private MemeResponse toMemeResponse(Meme meme) {
        MemeResponse r = new MemeResponse();
        r.setId(meme.getId());
        r.setTitle(meme.getTitle());
        r.setImageUrl(meme.getImageUrl());
        r.setUploadedBy(meme.getUploadedBy() != null ? meme.getUploadedBy().getUsername() : "Anonymous");
        r.setUploadDate(meme.getUploadDate());
        r.setVoteCount(meme.getVoteCount() != null ? meme.getVoteCount() : 0);
        r.setUserVoted(false);
        r.setUserVoteType(null);
        return r;
    }
}
