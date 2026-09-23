package com.gyw.backend.user.dto;

import com.gyw.backend.user.model.UserView;

import java.time.Instant;

public record LoginResponse(
        String accessToken, String tokenType, Instant expiresAt, UserView user) {
    @Override
    public String toString() {
        return "LoginResponse[token redacted]";
    }
}
