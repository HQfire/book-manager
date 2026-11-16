package org.example.bookmanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void hashShouldNotEqualRawPassword() {
        String raw = "secret123";
        String hash = PasswordUtil.hash(raw);
        assertEquals(60, hash.length());
        assertNotEquals(raw, hash);
    }

    @Test
    void matchesShouldAcceptCorrectPassword() {
        String raw = "secret123";
        String hash = PasswordUtil.hash(raw);
        assertTrue(PasswordUtil.matches(raw, hash));
    }

    @Test
    void matchesShouldRejectWrongPassword() {
        String hash = PasswordUtil.hash("secret123");
        assertFalse(PasswordUtil.matches("wrong-pass", hash));
    }

    @Test
    void matchesShouldRejectBlankInputs() {
        assertFalse(PasswordUtil.matches(null, PasswordUtil.hash("secret123")));
        assertFalse(PasswordUtil.matches("secret123", null));
        assertFalse(PasswordUtil.matches("secret123", ""));
    }
}
