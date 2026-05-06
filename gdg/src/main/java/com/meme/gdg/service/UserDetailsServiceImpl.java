package com.meme.gdg.service;

import com.meme.gdg.model.User;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        log.debug("Loading user by username/email: '{}'", usernameOrEmail);

        // Try username first
        Optional<User> byUsername = userRepository.findByUsername(usernameOrEmail);
        if (byUsername.isPresent()) {
            log.debug("Found user by username: '{}'", usernameOrEmail);
            return UserPrincipal.create(byUsername.get());
        }

        // Fall back to email
        Optional<User> byEmail = userRepository.findByEmail(usernameOrEmail);
        if (byEmail.isPresent()) {
            log.debug("Found user by email: '{}'", usernameOrEmail);
            return UserPrincipal.create(byEmail.get());
        }

        log.warn("User not found with username or email: '{}'", usernameOrEmail);
        throw new UsernameNotFoundException("User not found: " + usernameOrEmail);
    }
}
