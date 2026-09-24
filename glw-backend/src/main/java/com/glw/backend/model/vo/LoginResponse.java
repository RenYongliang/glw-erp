package com.glw.backend.model.vo;

import com.glw.backend.model.entity.UserView;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant expiresAt,
        Instant refreshExpiresAt,
        UserView user) {
    @Override
    public String toString() {
        return "LoginResponse[token redacted]";
    }
}
