package com.glw.backend.config;

import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration accessTtl,
        @NotNull Duration rememberTtl) {
    @AssertTrue(message = "JWT 有效期必须大于 0，保持登录有效期不得短于普通有效期")
    public boolean isValidTtl() {
        return accessTtl != null
                && rememberTtl != null
                && accessTtl.compareTo(Duration.ZERO) > 0
                && rememberTtl.compareTo(accessTtl) >= 0;
    }
}
