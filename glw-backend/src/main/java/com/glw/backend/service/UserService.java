package com.glw.backend.service;

import com.glw.backend.auth.*;
import com.glw.backend.common.*;
import com.glw.backend.model.dto.LoginRequest;
import com.glw.backend.model.entity.Account;
import com.glw.backend.model.entity.UserView;
import com.glw.backend.model.vo.LoginResponse;
import com.glw.backend.repository.AccountRepository;

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
        var pair = jwt.issue(account.id(), Boolean.TRUE.equals(request.remember()));
        sessions.save(pair.sessionId(), session(account.id(), pair));
        return response(account, pair);
    }

    public LoginResponse refresh(String refreshToken) {
        try {
            var identity = jwt.parseRefresh(refreshToken);
            Account account =
                    accounts.findById(identity.userId())
                            .filter(Account::enabled)
                            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            var pair = jwt.renew(account.id(), identity.sessionId(), identity.expiresAt());
            if (!sessions.rotate(
                    identity.sessionId(),
                    account.id(),
                    identity.tokenId(),
                    session(account.id(), pair)))
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            return response(account, pair);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private SessionStore.Session session(long userId, JwtService.Pair pair) {
        return new SessionStore.Session(
                userId, pair.access().id(), pair.refresh().id(), pair.refresh().expiresAt());
    }

    private LoginResponse response(Account account, JwtService.Pair pair) {
        return new LoginResponse(
                pair.access().value(),
                pair.refresh().value(),
                "Bearer",
                pair.access().expiresAt(),
                pair.refresh().expiresAt(),
                UserView.from(account));
    }

    public void logout(AuthPrincipal principal) {
        sessions.revoke(principal.sessionId());
    }
}
