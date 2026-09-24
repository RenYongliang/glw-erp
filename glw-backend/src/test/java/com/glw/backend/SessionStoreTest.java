package com.glw.backend;

import static org.assertj.core.api.Assertions.*;

import com.glw.backend.auth.*;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class SessionStoreTest {
    @Test
    void rotationIsAtomicAndLogoutCannotBeUndone() throws Exception {
        Instant now = Instant.now(), end = now.plusSeconds(600);
        var store = new InMemorySessionStore(Clock.fixed(now, ZoneOffset.UTC));
        store.save("sid", new SessionStore.Session(1, "a1", "r1", end));
        assertThat(store.active("sid", 2, "a1")).isFalse();
        AtomicInteger successes = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 20; i++) {
                final int n = i;
                executor.submit(
                        () -> {
                            if (store.rotate(
                                    "sid",
                                    1,
                                    "r1",
                                    new SessionStore.Session(1, "a" + n, "r" + n + "next", end)))
                                successes.incrementAndGet();
                        });
            }
        }
        assertThat(successes.get()).isEqualTo(1);
        store.revoke("sid");
        assertThat(store.rotate("sid", 1, "r1", new SessionStore.Session(1, "a", "r", end)))
                .isFalse();
        assertThat(store.active("sid", 1, "a1")).isFalse();
    }

    @Test
    void rejectsExpiredSessionsAndExtensionDuringRotation() {
        Instant now = Instant.now(), end = now.plusSeconds(60);
        var store = new InMemorySessionStore(Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(
                        () ->
                                store.save(
                                        "bad",
                                        new SessionStore.Session(1, "a", "r", now.minusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class);
        store.save("sid", new SessionStore.Session(1, "a", "r", end));
        assertThat(
                        store.rotate(
                                "sid",
                                1,
                                "r",
                                new SessionStore.Session(1, "a2", "r2", end.plusSeconds(60))))
                .isFalse();
    }
}
