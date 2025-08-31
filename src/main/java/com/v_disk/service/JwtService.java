package com.v_disk.service;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;

@Service
public class JwtService {
    private final Key jwtKey;
    private final long ttlSeconds;
    private final String issuer;

    public JwtService(Key jwtKey,
            @Value("${jwt.ttl.seconds:3600}") long ttlSeconds,
            @Value("${jwt.issuer:v-disk}") String issuer) {
        this.jwtKey = jwtKey;
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    /**
     * Generates a JWT token for the specified user with additional claims
     * 
     * @param username The user's username
     * @param extras   Additional claims to include in the token
     * @return JWT token as a string
     */
    public String generateToken(String username, Map<String, Object> extras) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plusSeconds(ttlSeconds));

        Map<String, Object> claims = new HashMap<>();
        // Add custom claims
        if (extras != null) {
            claims.putAll(extras);
        }

        return Jwts.builder()
                .setClaims(claims) // Set custom claims first
                .setSubject(username)
                .setIssuedAt(issuedAt)
                .setIssuer(issuer)
                .setExpiration(expiresAt)
                .signWith(jwtKey) // Uses default HS256 with JJWT 0.11.5
                .compact();
    }

    /**
     * Validates and parses a JWT token
     * 
     * @param token The JWT token string
     * @return Claims if token is valid
     * @throws JwtException if the token is invalid
     */
    public Claims validateToken(String token) throws JwtException {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (SignatureException | MalformedJwtException | ExpiredJwtException | UnsupportedJwtException
                | IllegalArgumentException e) {
            throw new JwtException("Invalid token: " + e.getMessage(), e);
        }
    }
}