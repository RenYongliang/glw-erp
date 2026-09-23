package com.gyw.backend.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数不正确"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    INVALID_CREDENTIALS(40101, HttpStatus.UNAUTHORIZED, "账号或密码不正确"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    CONFLICT(40900, HttpStatus.CONFLICT, "数据冲突"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务异常，请稍后重试"),
    SERVICE_UNAVAILABLE(50300, HttpStatus.SERVICE_UNAVAILABLE, "存储服务暂不可用，请稍后重试");
    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
