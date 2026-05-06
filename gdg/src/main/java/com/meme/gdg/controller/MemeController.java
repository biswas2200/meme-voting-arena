package com.meme.gdg.controller;

import com.meme.gdg.dto.MemeRequest;
import com.meme.gdg.dto.MemeResponse;
import com.meme.gdg.dto.MessageResponse;
import com.meme.gdg.dto.VoteRequest;
import com.meme.gdg.model.Meme;
import com.meme.gdg.service.MemeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/memes")
@Slf4j
public class MemeController {

    @Autowired
    private MemeService memeService;

    @GetMapping
    public ResponseEntity<Page<MemeResponse>> getAllMemes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Page<MemeResponse> memes = memeService.getAllMemes(page, size, null);
        return ResponseEntity.ok(memes);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<MemeResponse>> getLeaderboard() {
        List<MemeResponse> leaderboard = memeService.getLeaderboard(null);
        return ResponseEntity.ok(leaderboard);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createMeme(@Valid @RequestBody MemeRequest memeRequest,
                                        Authentication authentication) {
        try {
            MemeResponse memeResponse = memeService.createMeme(memeRequest, authentication);
            return ResponseEntity.ok(memeResponse);
        } catch (Exception e) {
            log.error("Error creating meme: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error creating meme: " + e.getMessage()));
        }
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadMeme(@RequestParam("file") MultipartFile file,
                                        @RequestParam("title") String title,
                                        @RequestParam(value = "description", required = false) String description,
                                        Authentication authentication) {
        try {
            MemeResponse memeResponse = memeService.uploadMeme(file, title, description, authentication);
            return ResponseEntity.ok(memeResponse);
        } catch (Exception e) {
            log.error("Error uploading meme: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error uploading meme: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> voteMeme(@PathVariable Long id,
                                      @Valid @RequestBody VoteRequest voteRequest,
                                      Authentication authentication) {
        try {
            MemeResponse memeResponse = memeService.voteMeme(id, voteRequest, authentication);
            return ResponseEntity.ok(memeResponse);
        } catch (Exception e) {
            log.error("Error voting on meme: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error voting on meme: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteMeme(@PathVariable Long id,
                                        Authentication authentication) {
        try {
            memeService.deleteMeme(id, authentication);
            return ResponseEntity.ok(new MessageResponse("Meme deleted successfully!"));
        } catch (Exception e) {
            log.error("Error deleting meme: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error deleting meme: " + e.getMessage()));
        }
    }

    @GetMapping("/battle")
    public ResponseEntity<List<Meme>> getBattleMemes() {
        List<Meme> battleMemes = memeService.getTwoRandomMemes();
        return ResponseEntity.ok(battleMemes);
    }
}
