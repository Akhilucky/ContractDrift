package com.contractsentinel.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_USER = "ROLE_USER";
    private static final Duration ADMIN_TOKEN_EXPIRY = Duration.ofHours(24);
    private static final Duration USER_TOKEN_EXPIRY = Duration.ofHours(1);

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${sentinel.auth.jwt-secret}") String jwtSecret) {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String serviceId, String role, Duration expiry) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plus(expiry));

        return Jwts.builder()
                .subject(serviceId)
                .claim("role", role)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String generateAdminToken(String serviceId) {
        return generateToken(serviceId, ROLE_ADMIN, ADMIN_TOKEN_EXPIRY);
    }

    public String generateUserToken(String serviceId) {
        return generateToken(serviceId, ROLE_USER, USER_TOKEN_EXPIRY);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String getServiceId(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.getSubject() : null;
    }

    public String getRole(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to parse JWT claims: {}", e.getMessage());
            return null;
        }
    }
}
