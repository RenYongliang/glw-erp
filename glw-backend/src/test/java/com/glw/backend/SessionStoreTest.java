package com.glw.backend;

import static org.assertj.core.api.Assertions.*;

import com.glw.backend.auth.InMemorySessionStore;

import org.junit.jupiter.api.Test;

import java.time.*;

class SessionStoreTest {
    @Test
    void expiresRevokesAndChecksUserIdentity() {
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        var store = new InMemorySessionStore(Clock.fixed(now, ZoneOffset.UTC));
        store.save("valid", 1, now.plusSeconds(60));
        store.save("expired", 1, now.minusSeconds(1));
        assertThat(store.active("valid", 1)).isTrue();
        assertThat(store.active("valid", 2)).isFalse();
        assertThat(store.active("expired", 1)).isFalse();
        assertThat(store.active("unknown", 1)).isFalse();
        store.revoke("valid");
        assertThat(store.active("valid", 1)).isFalse();
    }
}
