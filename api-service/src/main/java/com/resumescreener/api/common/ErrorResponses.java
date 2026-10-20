package com.resumescreener.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/** Filters run before the dispatcher, so GlobalExceptionHandler never sees their rejections. */
public final class ErrorResponses {

    private static final ObjectMapper WRITER = new ObjectMapper();

    private ErrorResponses() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, ApiErrorCode code,
                             String message, String details) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        WRITER.writeValue(response.getWriter(), ApiResponse.failure(message, new ApiError(code, details)));
    }
}
