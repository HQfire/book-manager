package org.example.bookmanager.util;

import org.example.bookmanager.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldRejectTamperedToken() {
        assertThrows(BizException.class, () -> jwtUtil.parseUserId("invalid.token.value"));
    }

    @Test
    void shouldRejectBlankToken() {
        assertThrows(BizException.class, () -> jwtUtil.parseUserId(""));
    }
}
