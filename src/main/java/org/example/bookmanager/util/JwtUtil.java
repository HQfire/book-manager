package org.example.bookmanager.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.bookmanager.common.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;

    @Autowired
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-minutes:120}") long expirationMinutes) {
        this(secret, expirationMinutes, 7);
    }

    public JwtUtil(String secret, long expirationMinutes, long refreshDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMillis = expirationMinutes * 60_000L;
        long days = refreshDays > 0 ? refreshDays : 7;
        this.refreshExpirationMillis = days * 24L * 60_000_000L;
    }

    public String generateToken(Long userId, String username) {
        return generateAccess(userId, username);
    }

    public String generateAccess(Long userId, String username) {
        return buildToken(userId, username, TYPE_ACCESS, accessExpirationMillis);
    }

    public String generateRefresh(Long userId, String username) {
        return buildToken(userId, username, TYPE_REFRESH, refreshExpirationMillis);
    }

    private String buildToken(Long userId, String username, String type, long expirationMillis) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String parseUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String parseType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(401, "invalid or expired token");
        }
    }

    public long getExpirationMillis() {
        return accessExpirationMillis;
    }
}
