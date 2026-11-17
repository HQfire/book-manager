package org.example.bookmanager.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private String refreshToken;
    private Long id;
    private String username;

    public LoginResponse(String token, String refreshToken, Long id, String username) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.id = id;
        this.username = username;
    }
}
