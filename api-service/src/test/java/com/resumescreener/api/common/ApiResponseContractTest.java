package com.resumescreener.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successResponseSerializesPredictably() throws Exception {
        ApiResponse<Map<String, String>> response =
                ApiResponse.success("JD uploaded successfully", Map.of("sessionId", "session-123"));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"message\":\"JD uploaded successfully\"");
        assertThat(json).contains("\"data\":{\"sessionId\":\"session-123\"}");
        assertThat(json).doesNotContain("\"error\"");
    }

    @Test
    void errorResponseSerializesPredictably() throws Exception {
        ApiResponse<Void> response =
                ApiResponse.failure("Failed to upload JD",
                        new ApiError(ApiErrorCode.MINIO_UNAVAILABLE, "Temporary failure in name resolution"));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"message\":\"Failed to upload JD\"");
        assertThat(json).contains("\"code\":\"MINIO_UNAVAILABLE\"");
        assertThat(json).contains("\"details\":\"Temporary failure in name resolution\"");
        assertThat(json).doesNotContain("\"data\"");
    }
}
