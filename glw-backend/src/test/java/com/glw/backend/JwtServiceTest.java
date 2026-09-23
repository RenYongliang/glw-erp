package com.glw.backend;

import static org.assertj.core.api.Assertions.*;

import com.glw.backend.auth.JwtService;
import com.glw.backend.config.JwtProperties;

import io.jsonwebtoken.JwtException;

import org.junit.jupiter.api.Test;

import java.time.*;

class JwtServiceTest {
    private final Instant now = Instant.parse("2026-09-23T00:00:00Z");
    private final String secret = "test-only-signing-key-at-least-thirty-two-bytes";

    private JwtService service(String issuer, String key, Instant time) {
        return new JwtService(
                new JwtProperties(issuer, key, Duration.ofHours(8), Duration.ofDays(7)),
                Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test
    void signsAndReadsIdentityAndUniqueSessionIds() {
        var service = service("glw-backend", secret, now);
        var token = service.issue(1, false);
        assertThat(service.parse(token.value()).userId()).isEqualTo(1);
        assertThat(service.parse(token.value()).tokenId()).isEqualTo(token.id());
        assertThat(token.id()).isNotEqualTo(service.issue(1, false).id());
    }

    @Test
    void rejectsExpiredWrongIssuerAndWrongSigningKey() {
        var token = service("glw-backend", secret, now).issue(1, false);
        assertThatThrownBy(
                        () ->
                                service("glw-backend", secret, now.plus(Duration.ofHours(9)))
                                        .parse(token.value()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service("different", secret, now).parse(token.value()))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(
                        () ->
                                service("glw-backend", secret + "different", now)
                                        .parse(token.value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedPayload() {
        var service = service("glw-backend", secret, now);
        String[] parts = service.issue(1, false).value().split("\\.");
        String changed =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "{\"sub\":\"999\"}"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.parse(parts[0] + "." + changed + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }
}
