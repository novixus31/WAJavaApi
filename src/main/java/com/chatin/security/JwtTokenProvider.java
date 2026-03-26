package com.chatin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationDays;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-days:7}") long expirationDays) {
        // Ensure the secret is at least 256 bits for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            this.secretKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
        this.expirationDays = expirationDays;
    }

    /**
     * Generate a JWT token for a user
     */
    public String generateToken(String userId, String email, String role, String companyId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationDays, ChronoUnit.DAYS);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("role", role);
        if (companyId != null) {
            claims.put("companyId", companyId);
        }

        return Jwts.builder()
                .subject(userId)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validate a JWT token and return claims
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extract user ID from token
     */
    public String getUserId(String token) {
        return validateToken(token).getSubject();
    }

    /**
     * Extract role from token
     */
    public String getRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    /**
     * Extract company ID from token
     */
    public String getCompanyId(String token) {
        return validateToken(token).get("companyId", String.class);
    }

    /**
     * Extract email from token
     */
    public String getEmail(String token) {
        return validateToken(token).get("email", String.class);
    }
}
