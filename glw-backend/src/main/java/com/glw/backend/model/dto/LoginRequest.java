package com.glw.backend.model.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank(message = "请输入账号") @Size(max = 64) String username,
        @NotBlank(message = "请输入密码") @Size(max = 72) String password,
        Boolean remember) {
    @Override
    public String toString() {
        return "LoginRequest[credentials redacted]";
    }
}
