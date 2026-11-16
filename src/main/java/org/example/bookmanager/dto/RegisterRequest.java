package org.example.bookmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(max = 50, message = "username must be at most 50 chars")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 6, max = 32, message = "password must be 6-32 chars")
    private String password;
}
