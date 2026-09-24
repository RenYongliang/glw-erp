package com.glw.backend.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.List;

@Component
@Profile("!mock")
public class RedisSessionStore implements SessionStore {
    private static final String PREFIX = "glw:auth:session:";
    private static final DefaultRedisScript<Long> ROTATE =
            new DefaultRedisScript<>(
                    """
local value = redis.call('GET', KEYS[1])
if not value then return 0 end
local current = cjson.decode(value)
local replacement = cjson.decode(ARGV[3])
if tostring(current.userId) ~= ARGV[1] or current.refreshId ~= ARGV[2]
  or replacement.userId ~= current.userId or replacement.expiresAt ~= current.expiresAt then return 0 end
redis.call('SET', KEYS[1], ARGV[3], 'KEEPTTL')
return 1
""",
                    Long.class);
    private final StringRedisTemplate redis;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final Clock clock;

    public RedisSessionStore(
            StringRedisTemplate redis, tools.jackson.databind.ObjectMapper mapper, Clock clock) {
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void save(String id, Session session) {
        Duration ttl = Duration.between(clock.instant(), session.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Expired session");
        redis.opsForValue().set(PREFIX + id, mapper.writeValueAsString(session), ttl);
    }

    public boolean active(String id, long userId, String accessId) {
        String value = redis.opsForValue().get(PREFIX + id);
        if (value == null) return false;
        Session s = mapper.readValue(value, Session.class);
        return s.userId() == userId
                && s.accessId().equals(accessId)
                && s.expiresAt().isAfter(clock.instant());
    }

    public boolean rotate(String id, long userId, String expected, Session replacement) {
        return Long.valueOf(1)
                .equals(
                        redis.execute(
                                ROTATE,
                                List.of(PREFIX + id),
                                Long.toString(userId),
                                expected,
                                mapper.writeValueAsString(replacement)));
    }

    public void revoke(String id) {
        redis.delete(PREFIX + id);
    }
}
