package com.meme.gdg.controller;

import com.meme.gdg.dto.*;
import com.meme.gdg.model.User;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.security.JwtUtils;
import com.meme.gdg.security.UserPrincipal;
import com.meme.gdg.service.KeywordService;
import com.meme.gdg.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private KeywordService keywordService;

    @Autowired
    private UserProfileService userProfileService;
    
    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.debug("Signin attempt for: {}", loginRequest.getUsername());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);
            
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
            
            // Create response in format expected by frontend
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", userDetails.getId());
            userInfo.put("username", userDetails.getUsername());
            userInfo.put("email", userDetails.getEmail());
            userInfo.put("role", userDetails.getRole());
            response.put("user", userInfo);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Authentication failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Invalid username or password!"));
        }
    }
    
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        try {
            if (userRepository.existsByUsername(signUpRequest.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Error: Username is already taken!"));
            }
            
            if (userRepository.existsByEmail(signUpRequest.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Error: Email is already in use!"));
            }
            
            // Create new user account
            User user = new User();
            user.setUsername(signUpRequest.getUsername());
            user.setEmail(signUpRequest.getEmail());
            user.setPassword(encoder.encode(signUpRequest.getPassword()));
            user.setRole(User.Role.USER);
            
            User savedUser = userRepository.save(user);
            
            // Generate JWT token for immediate login
            // Use email for authentication since our UserDetailsService supports both username and email
            String loginIdentifier = signUpRequest.getEmail(); // Use email instead of username for more reliable login
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginIdentifier, signUpRequest.getPassword()));
            
            String jwt = jwtUtils.generateJwtToken(authentication);
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
            
            // Create response in format expected by frontend
            Map<String, Object> signupResponse = new HashMap<>();
            signupResponse.put("token", jwt);
            
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", userDetails.getId());
            userData.put("username", userDetails.getUsername());
            userData.put("email", userDetails.getEmail());
            userData.put("role", userDetails.getRole());
            signupResponse.put("user", userData);
            
            return ResponseEntity.ok(signupResponse);
            
        } catch (Exception e) {
            log.error("Registration error: ", e);
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: Registration failed - " + e.getMessage()));
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUserAlternate(@Valid @RequestBody SignupRequest signUpRequest) {
        return registerUser(signUpRequest);
    }
    
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: User not authenticated!"));
        }
        UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(userProfileService.getUserStats(userDetails.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody UpdateProfileRequest req,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: User not authenticated!"));
        }
        try {
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
            UserStatsResponse updated = userProfileService.updateProfile(userDetails.getId(), req);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/profile/avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Error: User not authenticated!"));
        }
        try {
            UserPrincipal userDetails = (UserPrincipal) authentication.getPrincipal();
            UserStatsResponse updated = userProfileService.uploadAvatar(userDetails.getId(), file);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }
}
