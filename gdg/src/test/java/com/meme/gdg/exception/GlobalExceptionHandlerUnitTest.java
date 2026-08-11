package com.meme.gdg.exception;

import com.meme.gdg.dto.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler — verifies each @ExceptionHandler
 * maps its exception type to the correct HTTP status and response body.
 */
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidationExceptions_returnsBadRequestWithFieldErrors() {
        BindException bindException = new BindException(new Object(), "target");
        bindException.addError(new FieldError("target", "username", "Username is required"));
        bindException.addError(new FieldError("target", "email", "Email should be valid"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindException);

        ResponseEntity<Map<String, String>> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("username", "Username is required")
                .containsEntry("email", "Email should be valid");
    }

    @Test
    void handleAccessDenied_returnsForbiddenWithFixedMessage() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("insufficient scope");

        ResponseEntity<MessageResponse> response = handler.handleAccessDenied(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Admin access required");
    }

    @Test
    void handleBadCredentials_returnsUnauthorizedWithFixedMessage() {
        BadCredentialsException ex = new BadCredentialsException("bad creds");

        ResponseEntity<MessageResponse> response = handler.handleBadCredentials(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void handleDuplicateVote_returnsConflictWithExceptionMessage() {
        DuplicateVoteException ex = new DuplicateVoteException("You already voted on this matchup");

        ResponseEntity<MessageResponse> response = handler.handleDuplicateVote(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("You already voted on this matchup");
    }

    @Test
    void handleTournamentState_returnsConflictWithExceptionMessage() {
        TournamentStateException ex = new TournamentStateException("Tournament is not active");

        ResponseEntity<MessageResponse> response = handler.handleTournamentState(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Tournament is not active");
    }

    @Test
    void handleInsufficientMemes_returnsBadRequestWithExceptionMessage() {
        InsufficientMemesException ex = new InsufficientMemesException("Need at least 8 memes");

        ResponseEntity<MessageResponse> response = handler.handleInsufficientMemes(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Need at least 8 memes");
    }

    @Test
    void handleRuntimeException_returnsBadRequestWithExceptionMessage() {
        RuntimeException ex = new RuntimeException("Something went wrong");

        ResponseEntity<MessageResponse> response = handler.handleRuntimeException(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void handleGlobalException_returnsInternalServerErrorWithGenericMessage() {
        Exception ex = new Exception("unexpected low-level failure");

        ResponseEntity<MessageResponse> response = handler.handleGlobalException(ex, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }
}
