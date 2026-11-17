package org.example.bookmanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.dto.LoginRequest;
import org.example.bookmanager.dto.LoginResponse;
import org.example.bookmanager.dto.RegisterRequest;
import org.example.bookmanager.dto.UserDTO;
import org.example.bookmanager.entity.UserAccount;
import org.example.bookmanager.mapper.UserAccountMapper;
import org.example.bookmanager.service.LoginRateLimitService;
import org.example.bookmanager.service.RefreshTokenService;
import org.example.bookmanager.service.TokenBlacklistService;
import org.example.bookmanager.service.UserAccountService;
import org.example.bookmanager.util.JwtUtil;
import org.example.bookmanager.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountServiceImpl implements UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountServiceImpl.class);

    private final UserAccountMapper userAccountMapper;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;
    private final LoginRateLimitService rateLimitService;
    private final RefreshTokenService refreshTokenService;
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter loginLocked;

    public UserAccountServiceImpl(UserAccountMapper userAccountMapper, JwtUtil jwtUtil,
                                  TokenBlacklistService blacklistService,
                                  LoginRateLimitService rateLimitService,
                                  RefreshTokenService refreshTokenService,
                                  MeterRegistry meterRegistry) {
        this.userAccountMapper = userAccountMapper;
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
        this.rateLimitService = rateLimitService;
        this.refreshTokenService = refreshTokenService;
        this.loginSuccess = meterRegistry.counter("auth.login.success");
        this.loginFailure = meterRegistry.counter("auth.login.failure");
        this.loginLocked = meterRegistry.counter("auth.login.locked");
    }

    @Override
    @Transactional
    public UserDTO register(RegisterRequest request) {
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("username", request.getUsername());
        Long count = userAccountMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(409, "username already exists");
        }

        UserAccount user = new UserAccount();
        user.setUsername(request.getUsername());
        user.setPassword(PasswordUtil.hash(request.getPassword()));
        userAccountMapper.insert(user);
        log.info("user registered: id={}, username={}", user.getId(), user.getUsername());
        return new UserDTO(user.getId(), user.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        if (rateLimitService.isLocked(username)) {
            loginLocked.increment();
            log.warn("login blocked by rate limit: username={}", username);
            throw new BizException(429, "too many failed login attempts, please try later");
        }
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        UserAccount user = userAccountMapper.selectOne(wrapper);
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            rateLimitService.recordFailure(username);
            loginFailure.increment();
            log.warn("login failed: username={}", username);
            throw new BizException(401, "invalid username or password");
        }
        rateLimitService.reset(username);
        String accessToken = jwtUtil.generateAccess(user.getId(), user.getUsername());
        String refreshToken = jwtUtil.generateRefresh(user.getId(), user.getUsername());
        refreshTokenService.store(jwtUtil.parseClaims(refreshToken));
        loginSuccess.increment();
        return new LoginResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        Claims claims = refreshTokenService.validate(refreshToken);
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        refreshTokenService.delete(claims);

        String newAccess = jwtUtil.generateAccess(userId, username);
        String newRefresh = jwtUtil.generateRefresh(userId, username);
        refreshTokenService.store(jwtUtil.parseClaims(newRefresh));
        log.info("refresh token rotated: userId={}", userId);
        return new LoginResponse(newAccess, newRefresh, userId, username);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            blacklistService.revoke(accessToken);
        }
        refreshTokenService.revoke(refreshToken);
    }
}
