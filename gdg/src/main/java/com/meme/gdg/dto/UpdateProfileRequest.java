package com.meme.gdg.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    @Email(message = "Email should be valid")
    private String email;

    /** Optional — only processed when non-blank */
    @Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters")
    private String currentPassword;

    @Size(min = 6, max = 40, message = "New password must be between 6 and 40 characters")
    private String newPassword;
}
