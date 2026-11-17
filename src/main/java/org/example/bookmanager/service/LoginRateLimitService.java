package org.example.bookmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class LoginRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitService.class);
    private static final String KEY_PREFIX = "auth:login:fail:";
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;

    public LoginRateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isLocked(String username) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + username);
            if (value == null) {
                return false;
            }
            return Integer.parseInt(value) >= MAX_FAILURES;
        } catch (Exception e) {
            log.warn("login rate limit check failed, fail open: {}", e.getMessage());
            return false;
        }
    }

    public void recordFailure(String username) {
        try {
            String key = KEY_PREFIX + username;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, WINDOW_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("record login failure failed, fail open: {}", e.getMessage());
        }
    }

    public void reset(String username) {
        try {
            redisTemplate.delete(KEY_PREFIX + username);
        } catch (Exception e) {
            log.warn("reset login failure counter failed: {}", e.getMessage());
        }
    }
}
