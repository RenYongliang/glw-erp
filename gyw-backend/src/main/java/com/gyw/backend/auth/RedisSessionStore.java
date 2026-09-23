package com.gyw.backend.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.*;

@Component
@Profile("!mock")
public class RedisSessionStore implements SessionStore {
    private static final String PREFIX = "gyw:auth:session:";
    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisSessionStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void save(String tokenId, long userId, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Expired session");
        redis.opsForValue().set(PREFIX + tokenId, Long.toString(userId), ttl);
    }

    public boolean active(String tokenId, long userId) {
        return Long.toString(userId).equals(redis.opsForValue().get(PREFIX + tokenId));
    }

    public void revoke(String tokenId) {
        redis.delete(PREFIX + tokenId);
    }
}
