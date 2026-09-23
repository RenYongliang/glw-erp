package com.gyw.backend.common;

import org.slf4j.MDC;

import java.time.Instant;

/** 所有业务接口和异常共用的响应结构，HTTP 状态码仍表达错误类别。 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp, String traceId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data, Instant.now(), MDC.get("traceId"));
    }

    public static ApiResponse<Void> error(ErrorCode error, String message) {
        return new ApiResponse<>(error.code(), message, null, Instant.now(), MDC.get("traceId"));
    }
}
