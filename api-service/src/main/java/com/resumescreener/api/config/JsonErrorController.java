package com.resumescreener.api.config;

import com.resumescreener.api.common.ApiError;
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's whitelabel error page. Anything that reaches the container
 * error dispatch — unmapped paths, errors raised in filters, servlet-level failures —
 * would otherwise render HTML; here it leaves as the same envelope every other endpoint uses.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class JsonErrorController implements ErrorController {

    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String path = asText(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        ApiErrorCode code = switch (status) {
            case NOT_FOUND -> ApiErrorCode.NOT_FOUND;
            case UNAUTHORIZED -> ApiErrorCode.AUTHENTICATION_FAILED;
            case FORBIDDEN -> ApiErrorCode.ACCESS_DENIED;
            case BAD_REQUEST, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE, PAYLOAD_TOO_LARGE ->
                    ApiErrorCode.VALIDATION_ERROR;
            default -> ApiErrorCode.INTERNAL_ERROR;
        };

        String message = switch (status) {
            case NOT_FOUND -> "Endpoint not found";
            case UNAUTHORIZED -> "Unauthorized: missing or invalid token";
            case FORBIDDEN -> "Forbidden: insufficient permissions";
            case METHOD_NOT_ALLOWED -> "HTTP method not supported for this endpoint";
            case PAYLOAD_TOO_LARGE -> "File too large";
            default -> "Request failed";
        };

        String details = path == null
                ? status.getReasonPhrase()
                : status.value() + " " + status.getReasonPhrase() + " for " + path;

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(message, new ApiError(code, details)));
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (raw instanceof Integer value) {
            HttpStatus resolved = HttpStatus.resolve(value);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String asText(Object value) {
        return value == null ? null : value.toString();
    }
}
