package com.glw.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EncryptedLoginRequest(
        @NotBlank @Size(max = 36) String keyId,
        @NotBlank @Size(max = 36) String challenge,
        @NotBlank @Size(max = 344) String encryptedKey,
        @NotBlank @Size(max = 16) String iv,
        @NotBlank @Size(max = 4096) String ciphertext) {
    @Override
    public String toString() {
        return "EncryptedLoginRequest[REDACTED]";
    }
}
