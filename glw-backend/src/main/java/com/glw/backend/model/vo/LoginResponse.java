package com.glw.backend.model.vo;

import com.glw.backend.model.entity.UserView;

import java.time.Instant;

public record LoginResponse(
        String accessToken, String tokenType, Instant expiresAt, UserView user) {
    @Override
    public String toString() {
        return "LoginResponse[token redacted]";
    }
}
