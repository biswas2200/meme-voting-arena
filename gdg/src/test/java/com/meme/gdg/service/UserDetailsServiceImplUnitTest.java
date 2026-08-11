package com.meme.gdg.service;

import com.meme.gdg.model.User;
import com.meme.gdg.repository.UserRepository;
import com.meme.gdg.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserDetailsServiceImpl — verifies the username-then-email
 * lookup fallback used during authentication.
 */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplUnitTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("regularuser");
        testUser.setEmail("regular@test.com");
        testUser.setPassword("encoded_password");
        testUser.setRole(User.Role.USER);
    }

    @Test
    void loadUserByUsername_foundByUsername_returnsUserPrincipal() {
        when(userRepository.findByUsername("regularuser")).thenReturn(Optional.of(testUser));

        UserDetails result = userDetailsService.loadUserByUsername("regularuser");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(result.getUsername()).isEqualTo("regularuser");
        assertThat(((UserPrincipal) result).getId()).isEqualTo(1L);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void loadUserByUsername_notFoundByUsername_fallsBackToEmail() {
        when(userRepository.findByUsername("regular@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("regular@test.com")).thenReturn(Optional.of(testUser));

        UserDetails result = userDetailsService.loadUserByUsername("regular@test.com");

        assertThat(result.getUsername()).isEqualTo("regularuser");
    }

    @Test
    void loadUserByUsername_notFoundByEither_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadUserByUsername_adminUser_grantsAdminRole() {
        testUser.setRole(User.Role.ADMIN);
        when(userRepository.findByUsername("regularuser")).thenReturn(Optional.of(testUser));

        UserDetails result = userDetailsService.loadUserByUsername("regularuser");

        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }
}
