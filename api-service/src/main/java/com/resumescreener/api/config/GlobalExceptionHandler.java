package com.resumescreener.api.config;

import com.resumescreener.api.auth.EmailTakenException;
import com.resumescreener.api.common.ApiError;
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.UnknownHostException;

/**
 * Every failure leaves the API as {@code {success:false, message, error:{code, details}}}.
 * No handler may return a bare string, a bare map, or an empty body.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException e) {
        return envelope(e.getStatus(), e.getMessage(), e.getCode(), e.getDetails());
    }

    @ExceptionHandler(EmailTakenException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailTaken(EmailTakenException e) {
        return envelope(HttpStatus.CONFLICT, "Email already registered",
                ApiErrorCode.VALIDATION_ERROR, safeDetails(e));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return envelope(HttpStatus.UNAUTHORIZED, "Invalid email or password",
                ApiErrorCode.AUTHENTICATION_FAILED, safeDetails(e));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return envelope(HttpStatus.FORBIDDEN, safeMessage(e, "Access denied"),
                ApiErrorCode.ACCESS_DENIED, safeDetails(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String details = fieldError == null
                ? "Request validation failed"
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return envelope(HttpStatus.BAD_REQUEST, "Validation failed", ApiErrorCode.VALIDATION_ERROR, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        // An unknown enum value (a typo'd permission or role) names the value instead of a generic parse error
        if (e.getCause() instanceof InvalidFormatException f && f.getTargetType() != null && f.getTargetType().isEnum()) {
            return envelope(HttpStatus.BAD_REQUEST, "Unknown value " + f.getValue(),
                    ApiErrorCode.VALIDATION_ERROR, f.getValue() + " is not a valid " + f.getTargetType().getSimpleName());
        }
        return envelope(HttpStatus.BAD_REQUEST, "Malformed request body",
                ApiErrorCode.VALIDATION_ERROR, "Request body could not be parsed as JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return envelope(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                ApiErrorCode.VALIDATION_ERROR, e.getName() + " has an unsupported value");
    }

    /**
     * Spring raises this from the multipart resolver, outside the dispatcher's normal path;
     * without an explicit handler it lands on /error and renders HTML.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return envelope(HttpStatus.PAYLOAD_TOO_LARGE, "File too large",
                ApiErrorCode.VALIDATION_ERROR, "Upload exceeds the configured multipart size limit");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e) {
        return envelope(HttpStatus.BAD_REQUEST, "Invalid file upload",
                ApiErrorCode.VALIDATION_ERROR, safeDetails(e));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException e) {
        return envelope(HttpStatus.NOT_FOUND, "Endpoint not found",
                ApiErrorCode.NOT_FOUND, e.getRequestURL());
    }

    /**
     * Raised instead of NoHandlerFoundException when the default resource handler is mapped;
     * without this it falls through to the RuntimeException handler and reports 400 for a 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return envelope(HttpStatus.NOT_FOUND, "Endpoint not found",
                ApiErrorCode.NOT_FOUND, e.getResourcePath());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return envelope(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed",
                ApiErrorCode.VALIDATION_ERROR, safeDetails(e));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return envelope(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type",
                ApiErrorCode.VALIDATION_ERROR, safeDetails(e));
    }

    /** Actuator matches on the raw Accept header, so a browser asking for HTML lands here. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        return envelope(HttpStatus.NOT_ACCEPTABLE, "This endpoint only answers in JSON",
                ApiErrorCode.VALIDATION_ERROR, "Send Accept: application/json");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        // Never echo the SQL statement back to the client
        return envelope(HttpStatus.BAD_REQUEST, "Invalid or missing data",
                ApiErrorCode.VALIDATION_ERROR, "A required field was missing or violated a constraint");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e) {
        log.warn("Request failed: {}", e.toString());
        if (containsCause(e, UnknownHostException.class)) {
            return envelope(HttpStatus.BAD_GATEWAY, "Object storage is unavailable",
                    ApiErrorCode.MINIO_UNAVAILABLE, safeDetails(e));
        }
        return envelope(HttpStatus.BAD_REQUEST, safeMessage(e, "Request failed"),
                ApiErrorCode.VALIDATION_ERROR, safeDetails(e));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception e) {
        log.error("Unexpected error", e);
        return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                ApiErrorCode.INTERNAL_ERROR, safeDetails(e));
    }

    private static ResponseEntity<ApiResponse<Void>> envelope(
            HttpStatus status, String message, ApiErrorCode code, String details) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(message, new ApiError(code, details)));
    }

    private static boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String safeMessage(Throwable throwable, String fallback) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    private static String safeDetails(Throwable throwable) {
        return safeMessage(throwable, throwable.getClass().getSimpleName());
    }
}
