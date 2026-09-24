package com.glw.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(@NotNull List<@Valid PublicEndpoint> whitelist) {
    public record PublicEndpoint(
            @NotNull HttpMethod method, @NotBlank @Pattern(regexp = "/.*") String path) {}
}
