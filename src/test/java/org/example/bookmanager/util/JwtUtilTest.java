package org.example.bookmanager.util;

import io.jsonwebtoken.Claims;
import org.example.bookmanager.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("test-secret-key-0123456789abcdefghijklmnopqrstuvwxyz", 120);

    @Test
    void shouldRoundTripUserIdAndUsername() {
        String token = jwtUtil.generateToken(7L, "alice");
        assertEquals(7L, jwtUtil.parseUserId(token));
        assertEquals("alice", jwtUtil.parseUsername(token));
    }

    @Test
    void shouldIssueDistinctJti() {
        String first = jwtUtil.generateToken(7L, "alice");
        String second = jwtUtil.generateToken(7L, "alice");
        Claims firstClaims = jwtUtil.parseClaims(first);
        Claims secondClaims = jwtUtil.parseClaims(second);
        assertNotNull(firstClaims.getId());
        assertNotEquals(firstClaims.getId(), secondClaims.getId());
    }

    @Test
    void shouldRejectTamperedToken() {
        assertThrows(BizException.class, () -> jwtUtil.parseUserId("invalid.token.value"));
    }

    @Test
    void shouldRejectBlankToken() {
        assertThrows(BizException.class, () -> jwtUtil.parseUserId(""));
    }
}
