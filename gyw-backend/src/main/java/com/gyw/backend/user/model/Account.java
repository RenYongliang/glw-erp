package com.gyw.backend.user.model;

/** 内部凭据对象，禁止直接用作接口响应。 */
public record Account(
        long id,
        String username,
        String passwordHash,
        String name,
        String role,
        String department,
        boolean enabled) {
    @Override
    public String toString() {
        return "Account[id=" + id + ", username=" + username + "]";
    }
}
