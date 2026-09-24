package com.glw.backend.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("mock")
public class InMemorySessionStore implements SessionStore {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySessionStore(Clock clock) {
        this.clock = clock;
    }

    public void save(String id, Session session) {
        if (!session.expiresAt().isAfter(clock.instant()))
            throw new IllegalArgumentException("Expired session");
        sessions.put(id, session);
    }

    public boolean active(String id, long userId, String accessId) {
        Session s = sessions.get(id);
        return s != null
                && s.expiresAt().isAfter(clock.instant())
                && s.userId() == userId
                && s.accessId().equals(accessId);
    }

    public boolean rotate(String id, long userId, String expected, Session replacement) {
        AtomicBoolean changed = new AtomicBoolean();
        sessions.computeIfPresent(
                id,
                (k, s) -> {
                    if (!s.expiresAt().isAfter(clock.instant())) return null;
                    if (s.userId() == userId
                            && s.refreshId().equals(expected)
                            && replacement.userId() == userId
                            && replacement.expiresAt().equals(s.expiresAt())) {
                        changed.set(true);
                        return replacement;
                    }
                    return s;
                });
        return changed.get();
    }

    public void revoke(String id) {
        sessions.remove(id);
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        sessions.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(clock.instant()));
    }
}
