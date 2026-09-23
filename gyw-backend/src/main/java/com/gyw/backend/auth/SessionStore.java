package com.gyw.backend.auth;

import java.time.Instant;

/** 登录会话白名单。只存 jti/userId，不存密码或完整 Token。 */
public interface SessionStore {
    void save(String tokenId, long userId, Instant expiresAt);

    boolean active(String tokenId, long userId);

    void revoke(String tokenId);
}
