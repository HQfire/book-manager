package org.example.bookmanager.service;

import io.jsonwebtoken.Claims;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String KEY_PREFIX = "auth:refresh:";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    public RefreshTokenService(JwtUtil jwtUtil, RedisTemplate<String, String> redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    public void store(Claims claims) {
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            return;
        }
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, claims.getSubject(), remaining, TimeUnit.MILLISECONDS);
        }
    }

    public Claims validate(String refreshToken) {
        Claims claims = jwtUtil.parseClaims(refreshToken);
        if (!JwtUtil.TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw new BizException(401, "invalid refresh token");
        }
        String jti = claims.getId();
        Boolean exists = jti == null ? false : redisTemplate.hasKey(KEY_PREFIX + jti);
        if (!Boolean.TRUE.equals(exists)) {
            throw new BizException(401, "refresh token expired or revoked, please login again");
        }
        return claims;
    }

    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtUtil.parseClaims(refreshToken);
            String jti = claims.getId();
            if (jti != null && !jti.isBlank()) {
                redisTemplate.delete(KEY_PREFIX + jti);
                log.info("refresh token revoked: jti={}", jti);
            }
        } catch (BizException e) {
            log.warn("revoke refresh token skipped: {}", e.getMessage());
        }
    }

    public void delete(Claims claims) {
        String jti = claims.getId();
        if (jti != null && !jti.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + jti);
        }
    }
}
