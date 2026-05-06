package com.meme.gdg.component;

import com.meme.gdg.model.Meme;
import com.meme.gdg.model.User;
import com.meme.gdg.model.Vote;
import com.meme.gdg.repository.MemeRepository;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.repository.VoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds sample data on startup.
 * Only active on 'dev' and 'docker-dev' profiles — never runs in production.
 */
@Component
@Profile({"dev", "docker-dev"})
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MemeRepository memeRepository;
    
    @Autowired
    private VoteRepository voteRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Always reseed in dev/docker-dev — create ensures tables are fresh
        log.info("Seeding sample data...");
        initializeSampleData();
        log.info("Sample data initialized successfully!");
    }
    
    private void initializeSampleData() {
        // Create sample users
        List<User> users = createSampleUsers();
        
        // Create sample memes
        List<Meme> memes = createSampleMemes(users);
        
        // Create sample votes
        createSampleVotes(users, memes);
        
        // Update vote counts
        updateVoteCounts(memes);
    }
    
    private List<User> createSampleUsers() {
        List<User> users = new ArrayList<>();
        
        // Admin user
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@memearena.com");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole(User.Role.ADMIN);
        users.add(userRepository.save(admin));
        
        // Regular users
        String[] usernames = {"memeLord", "funnyGuy", "jokeQueen", "laughMaster", "giggleGirl", "chuckleChamp"};
        String[] emails = {"lord@meme.com", "funny@guy.com", "joke@queen.com", "laugh@master.com", "giggle@girl.com", "chuckle@champ.com"};
        
        for (int i = 0; i < usernames.length; i++) {
            User user = new User();
            user.setUsername(usernames[i]);
            user.setEmail(emails[i]);
            user.setPassword(passwordEncoder.encode("password123"));
            user.setRole(User.Role.USER);
            users.add(userRepository.save(user));
        }
        
        return users;
    }
    
    private List<Meme> createSampleMemes(List<User> users) {
        List<Meme> memes = new ArrayList<>();
        Random random = new Random();
        
        String[] titles = {
            "When you finally understand a programming joke",
            "Me explaining to my cat why I can't pause an online game",
            "POV: You're a JavaScript developer fixing CSS",
            "When your code works on the first try",
            "Trying to fix one bug and creating three more",
            "Me at 3 AM still debugging",
            "When the client says 'it should be easy to implement'",
            "My reaction when the code works without knowing why",
            "When you copy code from Stack Overflow and it works",
            "Explaining to non-programmers what I do for work",
            "When you realize you've been staring at the screen for 3 hours",
            "My face when I see my old code",
            "When someone says they can build an app like Facebook in a week",
            "Me trying to understand my own code from 6 months ago",
            "When the project manager asks for 'just a small change'",
            "My brain during code review vs during coding",
            "When you finally solve that bug that's been haunting you",
            "Me explaining why we need proper error handling",
            "The feeling when your regex finally works",
            "When you realize you've been overthinking a simple problem"
        };
        
        String[] imageUrls = {
            "https://images.unsplash.com/photo-1555952494-efd681c7e3f9?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1518773553398-650c184e0bb3?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1605379399642-870262d3d051?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1581291518857-4e27b48ff24e?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1489875347897-49f64b51c1f8?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1607706189992-eae578626c86?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1553877522-43269d4ea984?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1599507593499-a3f7d7d97667?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1511376777868-611b54f68947?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1587440871875-191322ee64b0?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1638618138635-91c815adee55?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1602992708529-c9fdb12905c9?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1574169208507-84376144848b?w=400&h=400&fit=crop",
            "https://images.unsplash.com/photo-1563013544-824ae1b704d3?w=400&h=400&fit=crop"
        };
        
        for (int i = 0; i < titles.length && i < imageUrls.length; i++) {
            Meme meme = new Meme();
            meme.setTitle(titles[i]);
            meme.setImageUrl(imageUrls[i]);
            meme.setUploadedBy(users.get(random.nextInt(users.size() - 1) + 1)); // Exclude admin from random selection
            meme.setVoteCount(0);
            
            memes.add(memeRepository.save(meme));
        }
        
        return memes;
    }
    
    private void createSampleVotes(List<User> users, List<Meme> memes) {
        Random random = new Random();
        
        // Create random votes
        for (User user : users) {
            // Skip admin for voting
            if (user.getRole() == User.Role.ADMIN) continue;
            
            // Each user votes on 60-80% of memes
            int votesToCast = (int) (memes.size() * (0.6 + random.nextDouble() * 0.2));
            List<Meme> shuffledMemes = new ArrayList<>(memes);
            java.util.Collections.shuffle(shuffledMemes);
            
            for (int i = 0; i < votesToCast && i < shuffledMemes.size(); i++) {
                Meme meme = shuffledMemes.get(i);
                
                Vote vote = new Vote();
                vote.setUser(user);
                vote.setMeme(meme);
                // 70% upvotes, 30% downvotes
                vote.setVoteType(random.nextDouble() < 0.7 ? Vote.VoteType.UPVOTE : Vote.VoteType.DOWNVOTE);
                
                voteRepository.save(vote);
            }
        }
    }
    
    private void updateVoteCounts(List<Meme> memes) {
        for (Meme meme : memes) {
            long upvotes = voteRepository.countUpvotesByMemeId(meme.getId());
            long downvotes = voteRepository.countDownvotesByMemeId(meme.getId());
            meme.setVoteCount((int) (upvotes - downvotes));
            memeRepository.save(meme);
        }
    }
}
