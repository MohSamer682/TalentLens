package com.resumescreener.api.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ApiErrorCode code;
    private final String details;

    public ApiException(HttpStatus status, ApiErrorCode code, String message, String details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String message, String details) {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, message, details);
    }

    public static ApiException validation(String message, String details) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR, message, details);
    }

    public static ApiException forbidden(String message, String details) {
        return new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED, message, details);
    }
}
