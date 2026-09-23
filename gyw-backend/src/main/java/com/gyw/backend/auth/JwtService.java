package com.gyw.backend.auth;

import com.gyw.backend.config.JwtProperties;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.Clock;
import java.util.*;

import javax.crypto.SecretKey;

@Service
public class JwtService {
    public record Token(String value, String id, Instant expiresAt) {}

    public record Identity(long userId, String tokenId, Instant expiresAt) {}

    private final JwtProperties properties;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Token issue(long userId, boolean remember) {
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(remember ? properties.rememberTtl() : properties.accessTtl());
        String id = UUID.randomUUID().toString();
        String value =
                Jwts.builder()
                        .issuer(properties.issuer())
                        .subject(Long.toString(userId))
                        .id(id)
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();
        return new Token(value, id, expiresAt);
    }

    public Identity parse(String token) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(properties.issuer())
                        .clock(() -> Date.from(clock.instant()))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        if (claims.getId() == null
                || claims.getId().isBlank()
                || claims.getExpiration() == null
                || claims.getIssuedAt() == null)
            throw new MalformedJwtException("Missing session claims");
        try {
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0) throw new NumberFormatException();
            return new Identity(userId, claims.getId(), claims.getExpiration().toInstant());
        } catch (NumberFormatException ex) {
            throw new MalformedJwtException("Invalid subject");
        }
    }
}
