package com.glw.backend.auth;

import com.glw.backend.config.JwtProperties;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;

import javax.crypto.SecretKey;

@Service
public class JwtService {
    public record Token(String value, String id, Instant expiresAt) {
        @Override
        public String toString() {
            return "Token[redacted]";
        }
    }

    public record Pair(Token access, Token refresh, String sessionId) {}

    public record Identity(long userId, String tokenId, String sessionId, Instant expiresAt) {}

    private final JwtProperties properties;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Pair issue(long userId, boolean remember) {
        return renew(
                userId,
                UUID.randomUUID().toString(),
                now().plus(remember ? properties.rememberTtl() : properties.refreshTtl()));
    }

    public Pair renew(long userId, String sid, Instant refreshEnd) {
        Instant current = now();
        if (!refreshEnd.isAfter(current)) throw new IllegalArgumentException("Expired session");
        Instant accessEnd = current.plus(properties.accessTtl());
        if (accessEnd.isAfter(refreshEnd)) accessEnd = refreshEnd;
        return new Pair(
                sign(userId, sid, "access", accessEnd),
                sign(userId, sid, "refresh", refreshEnd),
                sid);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private Token sign(long userId, String sid, String type, Instant end) {
        String id = UUID.randomUUID().toString();
        String value =
                Jwts.builder()
                        .issuer(properties.issuer())
                        .subject(Long.toString(userId))
                        .id(id)
                        .claim("sid", sid)
                        .claim("type", type)
                        .issuedAt(Date.from(now()))
                        .expiration(Date.from(end))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();
        return new Token(value, id, end);
    }

    public Identity parseAccess(String value) {
        return parse(value, "access");
    }

    public Identity parseRefresh(String value) {
        return parse(value, "refresh");
    }

    private Identity parse(String token, String type) {
        Claims c =
                Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer(properties.issuer())
                        .require("type", type)
                        .clock(() -> Date.from(clock.instant()))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        String sid = c.get("sid", String.class);
        if (c.getId() == null
                || c.getId().isBlank()
                || sid == null
                || sid.isBlank()
                || c.getExpiration() == null
                || c.getIssuedAt() == null)
            throw new MalformedJwtException("Missing session claims");
        try {
            long id = Long.parseLong(c.getSubject());
            if (id <= 0) throw new NumberFormatException();
            return new Identity(id, c.getId(), sid, c.getExpiration().toInstant());
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("Invalid subject");
        }
    }
}
