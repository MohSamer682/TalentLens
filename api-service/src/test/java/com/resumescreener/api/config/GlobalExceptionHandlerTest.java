package com.resumescreener.api.config;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void nullMessageRuntimeExceptionStillProducesEnvelope() {
        // NPEs usually carry a null message; handler must not itself throw
        ApiResponse<Void> body = handler.handleRuntimeException(new NullPointerException()).getBody();

        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isNotBlank();
        assertThat(body.getError().getCode()).isEqualTo(ApiErrorCode.VALIDATION_ERROR);
        assertThat(body.getError().getDetails()).isNotBlank();
    }

    @Test
    void badQueryParameterMapsTo400WithoutEchoingInput() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "<script>", com.resumescreener.common.session.ScoringScope.class, "scope", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getDetails()).isEqualTo("scope has an unsupported value");
    }

    @Test
    void accessDeniedMapsTo403WithAccessDeniedCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("Not authorized or session not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).contains("Not authorized");
        assertThat(response.getBody().getError().getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void wrongMethodAndContentTypeKeepTheirStatusInsteadOf500() {
        ResponseEntity<ApiResponse<Void>> method = handler.handleMethodNotAllowed(
                new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"));
        ResponseEntity<ApiResponse<Void>> media = handler.handleUnsupportedMediaType(
                new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(method.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(media.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(method.getBody().isSuccess()).isFalse();
        assertThat(media.getBody().getError().getDetails()).isNotBlank();
    }

    @Test
    void dataIntegrityViolationDoesNotLeakSql() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new org.springframework.dao.DataIntegrityViolationException(
                        "could not execute statement [ERROR: null value in column \"title\"]"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).doesNotContain("execute statement");
        assertThat(response.getBody().getError().getDetails()).doesNotContain("execute statement");
    }

    @Test
    void apiExceptionKeepsItsOwnStatusAndCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleApiException(
                new ApiException(HttpStatus.BAD_GATEWAY, ApiErrorCode.BATCH_WORKER_UNAVAILABLE,
                        "Failed to trigger batch worker", "Connection refused"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getError().getCode())
                .isEqualTo(ApiErrorCode.BATCH_WORKER_UNAVAILABLE);
        assertThat(response.getBody().getError().getDetails()).isEqualTo("Connection refused");
    }

    @Test
    void nameResolutionFailureReportsStorageUnavailable() {
        RuntimeException wrapped = new RuntimeException("Failed to upload JD",
                new IOException(new UnknownHostException("minio")));

        ResponseEntity<ApiResponse<Void>> response = handler.handleRuntimeException(wrapped);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getError().getCode()).isEqualTo(ApiErrorCode.MINIO_UNAVAILABLE);
    }

    @Test
    void oversizedUploadReturnsJsonRatherThanFallingThroughToTheErrorPage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(10 * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getMessage()).isEqualTo("File too large");
        assertThat(response.getBody().getError().getCode()).isEqualTo(ApiErrorCode.VALIDATION_ERROR);
    }

    @Test
    void unknownPathReports404NotABadRequest() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResource(
                new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError().getCode()).isEqualTo(ApiErrorCode.NOT_FOUND);
    }

    @Test
    void unexpectedExceptionIsInternalErrorNotAValidationError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneralException(new Exception("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getCode()).isEqualTo(ApiErrorCode.INTERNAL_ERROR);
    }
}
