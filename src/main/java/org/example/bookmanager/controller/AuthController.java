package org.example.bookmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.bookmanager.common.Result;
import org.example.bookmanager.dto.LoginRequest;
import org.example.bookmanager.dto.LoginResponse;
import org.example.bookmanager.dto.RegisterRequest;
import org.example.bookmanager.dto.UserDTO;
import org.example.bookmanager.service.UserAccountService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountService userAccountService;

    public AuthController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @PostMapping("/register")
    public Result<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userAccountService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userAccountService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer "))
                ? header.substring("Bearer ".length()).trim() : null;
        userAccountService.logout(token);
        return Result.ok(null);
    }
}
