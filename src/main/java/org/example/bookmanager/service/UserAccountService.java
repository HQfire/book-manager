package org.example.bookmanager.service;

import org.example.bookmanager.dto.LoginRequest;
import org.example.bookmanager.dto.LoginResponse;
import org.example.bookmanager.dto.RegisterRequest;
import org.example.bookmanager.dto.UserDTO;

public interface UserAccountService {

    UserDTO register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String token);
}
