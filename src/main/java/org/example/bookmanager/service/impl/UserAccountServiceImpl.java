package org.example.bookmanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.dto.LoginRequest;
import org.example.bookmanager.dto.LoginResponse;
import org.example.bookmanager.dto.RegisterRequest;
import org.example.bookmanager.dto.UserDTO;
import org.example.bookmanager.entity.UserAccount;
import org.example.bookmanager.mapper.UserAccountMapper;
import org.example.bookmanager.service.LoginRateLimitService;
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

    public UserAccountServiceImpl(UserAccountMapper userAccountMapper, JwtUtil jwtUtil,
                                  TokenBlacklistService blacklistService,
                                  LoginRateLimitService rateLimitService) {
        this.userAccountMapper = userAccountMapper;
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
        this.rateLimitService = rateLimitService;
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
            log.warn("login blocked by rate limit: username={}", username);
            throw new BizException(429, "too many failed login attempts, please try later");
        }
        QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        UserAccount user = userAccountMapper.selectOne(wrapper);
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPassword())) {
            rateLimitService.recordFailure(username);
            log.warn("login failed: username={}", username);
            throw new BizException(401, "invalid username or password");
        }
        rateLimitService.reset(username);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            blacklistService.revoke(token);
        }
    }
}
