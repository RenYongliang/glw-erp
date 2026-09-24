package com.glw.backend;

import static org.assertj.core.api.Assertions.*;

import com.glw.backend.auth.JwtService;
import com.glw.backend.config.JwtProperties;

import io.jsonwebtoken.JwtException;

import org.junit.jupiter.api.Test;

import java.time.*;

class JwtServiceTest {
    private final Instant now = Instant.parse("2026-09-24T00:00:00Z");
    private final String secret = "test-only-signing-key-at-least-thirty-two-bytes";

    private JwtService service(String issuer, String key, Instant time) {
        return new JwtService(
                new JwtProperties(
                        issuer,
                        key,
                        Duration.ofMinutes(15),
                        Duration.ofHours(8),
                        Duration.ofDays(7)),
                Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test
    void typesAreNotInterchangeable() {
        var jwt = service("glw", secret, now);
        var pair = jwt.issue(1, false);
        assertThat(jwt.parseAccess(pair.access().value()).sessionId()).isEqualTo(pair.sessionId());
        assertThat(jwt.parseRefresh(pair.refresh().value()).userId()).isEqualTo(1);
        assertThatThrownBy(() -> jwt.parseAccess(pair.refresh().value()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parseRefresh(pair.access().value()))
                .isInstanceOf(JwtException.class);
        assertThat(pair.access().id()).isNotEqualTo(pair.refresh().id());
    }

    @Test
    void rejectsExpiryTamperingAndWrongIssuer() {
        var pair = service("glw", secret, now).issue(1, false);
        assertThatThrownBy(
                        () ->
                                service("glw", secret, now.plusSeconds(901))
                                        .parseAccess(pair.access().value()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service("other", secret, now).parseAccess(pair.access().value()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(
                        () ->
                                service("glw", secret + "different", now)
                                        .parseAccess(pair.access().value()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(
                        () ->
                                service("glw", secret, now.plusSeconds(28801))
                                        .parseRefresh(pair.refresh().value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rememberOnlyExtendsRefreshAndRefreshNeverExtendsAbsoluteSession() {
        var jwt = service("glw", secret, now);
        var normal = jwt.issue(1, false);
        var remembered = jwt.issue(1, true);
        assertThat(normal.access().expiresAt()).isEqualTo(remembered.access().expiresAt());
        assertThat(remembered.refresh().expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        var late = service("glw", secret, now.plus(Duration.ofHours(7)).plusSeconds(3590));
        var renewed = late.renew(1, normal.sessionId(), normal.refresh().expiresAt());
        assertThat(renewed.access().expiresAt()).isEqualTo(normal.refresh().expiresAt());
        assertThat(renewed.refresh().expiresAt()).isEqualTo(normal.refresh().expiresAt());
    }
}
