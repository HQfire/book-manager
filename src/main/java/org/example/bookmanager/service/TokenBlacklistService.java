package org.example.bookmanager.service;

import io.jsonwebtoken.Claims;
import org.example.bookmanager.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);
    private static final String KEY_PREFIX = "auth:blacklist:";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    public TokenBlacklistService(JwtUtil jwtUtil, RedisTemplate<String, String> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    public void revoke(String token) {
        Claims claims = jwtUtil.parseClaims(token);
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            return;
        }
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", remaining, TimeUnit.MILLISECONDS);
            log.info("token revoked: jti={}, remainingMs={}", jti, remaining);
        }
    }

    public boolean isRevoked(Claims claims) {
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Boolean revoked = redisTemplate.hasKey(KEY_PREFIX + jti);
        return Boolean.TRUE.equals(revoked);
    }
}
