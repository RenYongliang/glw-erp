package com.glw.backend.user.model;

/** 用字符串返回用户 ID，避免前端 JavaScript 大整数精度丢失。 */
public record UserView(String id, String username, String name, String role, String department) {
    public static UserView from(Account account) {
        return new UserView(
                Long.toString(account.id()),
                account.username(),
                account.name(),
                account.role(),
                account.department());
    }
}
