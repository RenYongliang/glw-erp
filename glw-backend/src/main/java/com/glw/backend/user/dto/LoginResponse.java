package com.glw.backend.user.dto;

import com.glw.backend.user.model.UserView;

import java.time.Instant;

public record LoginResponse(
        String accessToken, String tokenType, Instant expiresAt, UserView user) {
    @Override
    public String toString() {
        return "LoginResponse[token redacted]";
    }
}
