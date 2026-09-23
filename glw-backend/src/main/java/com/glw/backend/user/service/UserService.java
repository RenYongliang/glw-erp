package com.glw.backend.user.service;

import com.glw.backend.auth.*;
import com.glw.backend.common.*;
import com.glw.backend.user.dto.*;
import com.glw.backend.user.model.*;
import com.glw.backend.user.repository.AccountRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {
    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SessionStore sessions;
    private final String dummyHash;

    public UserService(
            AccountRepository accounts,
            PasswordEncoder encoder,
            JwtService jwt,
            SessionStore sessions) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.jwt = jwt;
        this.sessions = sessions;
        this.dummyHash = encoder.encode("nonexistent-account-placeholder");
    }

    public LoginResponse login(LoginRequest request) {
        // BCrypt 的上限为 72 字节，不只是 72 个字符。
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度不能超过 72 字节");
        Account account = accounts.findByUsername(request.username().trim()).orElse(null);
        boolean matches =
                encoder.matches(
                        request.password(), account == null ? dummyHash : account.passwordHash());
        if (account == null || !matches || !account.enabled())
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        JwtService.Token token = jwt.issue(account.id(), Boolean.TRUE.equals(request.remember()));
        sessions.save(token.id(), account.id(), token.expiresAt());
        return new LoginResponse(
                token.value(), "Bearer", token.expiresAt(), UserView.from(account));
    }

    public void logout(AuthPrincipal principal) {
        sessions.revoke(principal.tokenId());
    }
}
