package com.register.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Encodes and decodes the admin JWTs issued at login and verified on every subsequent
 * {@code /api/admin/**} request. Stateless — the signed token itself (subject + role claim) is the only
 * source of truth per request, no server-side session or token store.
 */
@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * Creates the JWT service from the configured signing secret and expiration.
     *
     * @param secret       the HMAC signing secret, bound from {@code app.jwt.secret}
     *                     ({@code JWT_SECRET} env var) — must be at least 256 bits (32 bytes) for HS256
     * @param expirationMs how long, in milliseconds, an issued token remains valid, bound from
     *                     {@code app.jwt.expiration-ms} ({@code JWT_EXPIRATION_MS} env var)
     */
    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT for a successfully authenticated admin user.
     *
     * @param username the admin's username, stored as the token subject
     * @param role     the admin's role (e.g. {@code ROLE_ADMIN}), stored as a custom claim
     * @return the compact, signed JWT string
     */
    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and verifies a JWT, returning its claims.
     *
     * @param token the compact JWT string (without the {@code "Bearer "} prefix)
     * @return the verified claims
     * @throws io.jsonwebtoken.JwtException if the token is malformed, has an invalid signature, or is expired
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the role claim from previously-parsed claims.
     *
     * @param claims the verified claims
     * @return the role claim value, or {@code null} if absent
     */
    public String extractRole(Claims claims) {
        return claims.get(ROLE_CLAIM, String.class);
    }

}
