package com.glw.backend.auth;

import java.time.Instant;

/** Stores login sessions and JWT IDs, never complete tokens or passwords. */
public interface SessionStore {
    record Session(long userId, String accessId, String refreshId, Instant expiresAt) {}

    void save(String sessionId, Session session);

    boolean active(String sessionId, long userId, String accessId);

    boolean rotate(String sessionId, long userId, String expectedRefreshId, Session replacement);

    void revoke(String sessionId);
}
