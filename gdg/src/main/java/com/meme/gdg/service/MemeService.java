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
import com.meme.gdg.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class MemeService {
    
    @Autowired
    private MemeRepository memeRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private VoteRepository voteRepository;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public Page<MemeResponse> getAllMemes(int page, int size, Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("voteCount").descending());
        Page<Meme> memes = memeRepository.findAllByOrderByVoteCountDesc(pageable);
        
        return memes.map(meme -> convertToMemeResponse(meme, authentication));
    }
    
    public List<MemeResponse> getLeaderboard(Authentication authentication) {
        List<Meme> topMemes = memeRepository.findTop5ByOrderByVoteCountDesc();
        return topMemes.stream()
                .map(meme -> convertToMemeResponse(meme, authentication))
                .collect(Collectors.toList());
    }
    
    public MemeResponse createMeme(MemeRequest memeRequest, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new RuntimeException("Authentication required");
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Meme meme = new Meme();
        meme.setTitle(memeRequest.getTitle());
        meme.setImageUrl(memeRequest.getImageUrl());
        meme.setUploadedBy(user);
        meme.setVoteCount(0);
        
        Meme savedMeme = memeRepository.save(meme);
        return convertToMemeResponse(savedMeme, authentication);
    }
    
    public MemeResponse uploadMeme(MultipartFile file, String title, String description, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new RuntimeException("Authentication required");
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Validate file
        if (file.isEmpty()) {
            throw new RuntimeException("Please select a file to upload");
        }
        
        // Check file size (1MB = 1024 * 1024 bytes)
        long maxFileSize = 1024 * 1024; // 1MB
        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("File size must be less than 1MB");
        }
        
        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Please upload a valid image file");
        }
        
        try {
            Path uploadDir = Paths.get("uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath);
            String imageUrl = "/uploads/" + filename;

            // Create meme record with the resolved image URL
            Meme meme = new Meme();
            meme.setTitle(title);
            meme.setImageUrl(imageUrl);
            meme.setUploadedBy(user);
            meme.setVoteCount(0);

            Meme savedMeme = memeRepository.save(meme);
            return convertToMemeResponse(savedMeme, authentication);
            
        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }
    
    public MemeResponse voteMeme(Long memeId, VoteRequest voteRequest, Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Authentication required");
        }
        Meme meme = memeRepository.findById(memeId)
                .orElseThrow(() -> new RuntimeException("Meme not found"));
        
        if (voteRequest.getVoteType() == Vote.VoteType.UPVOTE) {
            meme.setVoteCount(meme.getVoteCount() + 1);
        } else {
            meme.setVoteCount(meme.getVoteCount() - 1);
        }
        
        Meme savedMeme = memeRepository.save(meme);
        
        // Send real-time update via WebSocket
        MemeResponse response = convertToMemeResponse(savedMeme, authentication);
        messagingTemplate.convertAndSend("/topic/votes", response);
        
        return response;
    }
    
    public void deleteMeme(Long memeId, Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Authentication required");
        }
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Meme meme = memeRepository.findById(memeId)
                .orElseThrow(() -> new RuntimeException("Meme not found"));
        
        // Check if user is admin or meme owner
        if (!userPrincipal.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")) &&
            !meme.getUploadedBy().getId().equals(userPrincipal.getId())) {
            throw new RuntimeException("Access denied");
        }
        
        memeRepository.delete(meme);
    }
    
    /**
     * Returns 2 distinct memes chosen at random.
     * Randomness is handled in the service layer (Java Collections.shuffle)
     * so no DB-specific random function is needed — works on H2, PostgreSQL, etc.
     */
    public List<Meme> getTwoRandomMemes() {
        long total = memeRepository.count();
        if (total < 2) {
            throw new RuntimeException("Not enough memes available for a battle. Upload at least 2 memes first.");
        }

        // Fetch all IDs via a lightweight pageable query and shuffle in memory.
        // For large datasets this can be replaced with a keyset-pagination approach,
        // but for a meme app this is perfectly fine.
        List<Meme> all = memeRepository.findAllOrderById(Pageable.unpaged());
        List<Meme> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, 2);
    }
    
    private void updateMemeVoteCount(Meme meme) {
        long upvotes = voteRepository.countUpvotesByMemeId(meme.getId());
        long downvotes = voteRepository.countDownvotesByMemeId(meme.getId());
        meme.setVoteCount((int) (upvotes - downvotes));
    }
    
    private MemeResponse convertToMemeResponse(Meme meme, Authentication authentication) {
        MemeResponse response = new MemeResponse();
        response.setId(meme.getId());
        response.setTitle(meme.getTitle());
        response.setImageUrl(meme.getImageUrl());
        response.setUploadedBy(meme.getUploadedBy() != null ? meme.getUploadedBy().getUsername() : "Anonymous");
        response.setUploadDate(meme.getUploadDate());
        response.setVoteCount(meme.getVoteCount());
        
        if (authentication != null && authentication.isAuthenticated()) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            Optional<Vote> userVote = voteRepository.findByUserIdAndMemeId(userPrincipal.getId(), meme.getId());
            response.setUserVoted(userVote.isPresent());
            response.setUserVoteType(userVote.map(vote -> vote.getVoteType().name()).orElse(null));
        } else {
            response.setUserVoted(false);
            response.setUserVoteType(null);
        }
        
        return response;
    }
}
