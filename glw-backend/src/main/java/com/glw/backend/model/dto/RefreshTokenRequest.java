package com.glw.backend.model.dto;

import jakarta.validation.constraints.*;

public record RefreshTokenRequest(@NotBlank @Size(max = 8192) String refreshToken) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[redacted]";
    }
}
