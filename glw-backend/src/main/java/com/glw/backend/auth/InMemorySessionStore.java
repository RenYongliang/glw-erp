package com.glw.backend.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("mock")
public class InMemorySessionStore implements SessionStore {
    private record Session(long userId, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySessionStore(Clock clock) {
        this.clock = clock;
    }

    public void save(String tokenId, long userId, Instant expiresAt) {
        sessions.put(tokenId, new Session(userId, expiresAt));
    }

    public boolean active(String tokenId, long userId) {
        Session session = sessions.get(tokenId);
        if (session == null) return false;
        if (!session.expiresAt().isAfter(clock.instant())) {
            sessions.remove(tokenId, session);
            return false;
        }
        return session.userId() == userId;
    }

    public void revoke(String tokenId) {
        sessions.remove(tokenId);
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        sessions.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(clock.instant()));
    }
}
