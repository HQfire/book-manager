package org.example.bookmanager.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.service.TokenBlacklistService;
import org.example.bookmanager.util.JwtUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USERNAME = "currentUsername";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    public AuthInterceptor(JwtUtil jwtUtil, TokenBlacklistService blacklistService) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(401, "missing or invalid Authorization header");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        Claims claims = jwtUtil.parseClaims(token);
        if (blacklistService.isRevoked(claims)) {
            throw new BizException(401, "token revoked, please login again");
        }
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_USERNAME, username);
        return true;
    }
}
