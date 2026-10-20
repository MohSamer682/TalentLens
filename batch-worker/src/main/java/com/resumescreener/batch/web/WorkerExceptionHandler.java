package com.resumescreener.batch.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Same {success, message, error:{code, details}} envelope as api-service, so no worker answer has an empty or default body. */
@RestControllerAdvice
@Slf4j
public class WorkerExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return failure(HttpStatus.BAD_REQUEST, "Malformed request body", "VALIDATION_ERROR", "Request body could not be parsed as JSON");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        // 400/404/405/415 from Spring MVC and ResponseStatusException all carry their own status
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = er.getStatusCode();
            String detail = er.getBody().getDetail();
            return failure(status, "Request rejected", status.value() == 404 ? "NOT_FOUND" : "VALIDATION_ERROR",
                    detail == null ? e.getMessage() : detail);
        }
        log.error("Unexpected worker error", e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_ERROR", e.getClass().getSimpleName());
    }

    private static ResponseEntity<Map<String, Object>> failure(HttpStatusCode status, String message, String code, String details) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message,
                "error", Map.of("code", code, "details", String.valueOf(details))));
    }
}
