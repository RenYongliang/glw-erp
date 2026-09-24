package com.glw.backend.auth;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.model.dto.EncryptedLoginRequest;
import com.glw.backend.model.dto.LoginRequest;

import jakarta.validation.Validator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.time.*;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.spec.*;

@Service
public class LoginEncryptionService {
    public record LoginKey(String keyId, String publicKey, String challenge, Instant expiresAt) {}

    private final KeyPair keys;
    private final String keyId = UUID.randomUUID().toString();
    private final Map<String, Instant> challenges = new HashMap<>();
    private final Clock clock;
    private final ObjectMapper json;
    private final Validator validator;

    public LoginEncryptionService(Clock clock, ObjectMapper json, Validator validator)
            throws GeneralSecurityException {
        this.clock = clock;
        this.json = json;
        this.validator = validator;
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void cleanup() {
        challenges.values().removeIf(end -> !end.isAfter(clock.instant()));
    }

    public synchronized LoginKey issue() {
        cleanup();
        if (challenges.size() >= 10000)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录请求过多，请稍后重试");
        String challenge = UUID.randomUUID().toString();
        Instant end = clock.instant().plusSeconds(120);
        challenges.put(challenge, end);
        return new LoginKey(
                keyId,
                Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),
                challenge,
                end);
    }

    private synchronized boolean consume(EncryptedLoginRequest request) {
        if (!keyId.equals(request.keyId())) return false;
        Instant end = challenges.remove(request.challenge());
        return end != null && end.isAfter(clock.instant());
    }

    public LoginRequest decrypt(EncryptedLoginRequest request) {
        byte[] secret = null;
        byte[] plaintext = null;
        try {
            if (!consume(request)) throw new IllegalArgumentException();
            var decoder = Base64.getDecoder();
            byte[] wrapped = decoder.decode(request.encryptedKey());
            byte[] iv = decoder.decode(request.iv());
            byte[] ciphertext = decoder.decode(request.ciphertext());
            if (wrapped.length != 256
                    || iv.length != 12
                    || ciphertext.length < 16
                    || ciphertext.length > 3072) throw new IllegalArgumentException();
            var rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(
                    Cipher.DECRYPT_MODE,
                    keys.getPrivate(),
                    new OAEPParameterSpec(
                            "SHA-256",
                            "MGF1",
                            MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT));
            secret = rsa.doFinal(wrapped);
            if (secret.length != 32) throw new IllegalArgumentException();
            var aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(secret, "AES"),
                    new GCMParameterSpec(128, iv));
            aes.updateAAD(
                    (request.keyId() + "." + request.challenge()).getBytes(StandardCharsets.UTF_8));
            plaintext = aes.doFinal(ciphertext);
            LoginRequest login = json.readValue(plaintext, LoginRequest.class);
            if (login == null || !validator.validate(login).isEmpty())
                throw new IllegalArgumentException();
            return login;
        } catch (Exception ignored) {
            // Never include ciphertext, plaintext, or crypto exception details in logs/errors.
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录加密数据无效或已过期，请重新登录");
        } finally {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }
}
