package com.gyw.backend.common;

public class BusinessException extends RuntimeException {
    private final ErrorCode error;

    public BusinessException(ErrorCode error) {
        this(error, error.message());
    }

    public BusinessException(ErrorCode error, String message) {
        super(message);
        this.error = error;
    }

    public ErrorCode error() {
        return error;
    }
}
