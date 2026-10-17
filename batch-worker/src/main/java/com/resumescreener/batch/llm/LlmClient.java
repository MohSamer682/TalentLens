package com.resumescreener.batch.llm;

import com.resumescreener.common.llm.LlmScoreDto;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmClient {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.base-url}")
    private String baseUrl;

    private final RestClient restClient;
    private final LlmScoreParser parser;

    @Retry(name = "llm")
    @RateLimiter(name = "llm")
    public LlmScoreDto score(String jdText, String resumeText) {
        validateConfig();

        Map<String, Object> body = Map.of(
                "model", model.trim(),
                "messages", List.of(
                        Map.of("role", "system", "content", getSystemPrompt()),
                        Map.of("role", "user", "content", buildUserPrompt(jdText, resumeText))
                ),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );

        LlmChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri(normalizedBaseUrl() + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(LlmChatCompletionResponse.class);
        } catch (HttpServerErrorException e) {
            log.warn("LLM server error: HTTP {}", e.getStatusCode().value());
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("LLM connection failed: {}", e.getMessage());
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("LLM rate limited by provider (HTTP 429)");
            throw new LlmRetryableException("LLM provider rate limit (HTTP 429)", e);
        } catch (RestClientResponseException e) {
            log.error("LLM provider error: HTTP {}", e.getStatusCode().value(), e);
            throw new LlmException("LLM provider error: HTTP " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("LLM request failed", e);
            throw new LlmException("LLM provider request failed: " + e.getMessage(), e);
        }

        return parser.parseContent(extractContent(response));
    }

    void validateConfig() {
        if (isBlank(apiKey)) {
            throw new LlmException("Missing LLM API key configuration");
        }
        if (isBlank(model)) {
            throw new LlmException("Missing LLM model configuration");
        }
        if (isBlank(baseUrl)) {
            throw new LlmException("Missing LLM base URL configuration");
        }
    }

    private String normalizedBaseUrl() {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String chatCompletionsPath = "/chat/completions";
        if (normalized.endsWith(chatCompletionsPath)) {
            normalized = normalized.substring(0, normalized.length() - chatCompletionsPath.length());
        }
        return normalized;
    }

    private String extractContent(LlmChatCompletionResponse response) {
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new LlmRetryableException("No choices in LLM response");
        }

        Choice firstChoice = response.choices.get(0);
        if (firstChoice == null || firstChoice.message == null || isBlank(firstChoice.message.content)) {
            throw new LlmRetryableException("No content in LLM response choice");
        }

        return firstChoice.message.content;
    }

    private String getSystemPrompt() {
        return "You are an expert HR analyst. Evaluate the resume against the job description.\n" +
                "Return ONLY valid JSON matching the schema below. No prose outside JSON.\n\n" +
                "Schema:\n" +
                "{\n" +
                "  \"technical_skills\": <0-100>,\n" +
                "  \"experience\": <0-100>,\n" +
                "  \"education\": <0-100>,\n" +
                "  \"soft_skills\": <0-100>,\n" +
                "  \"culture_fit\": <0-100>,\n" +
                "  \"skills\": [\"skill1\", \"skill2\", ...],\n" +
                "  \"justification\": \"<2-3 sentence overall assessment>\",\n" +
                "  \"strengths\": \"<key strengths>\",\n" +
                "  \"gaps\": \"<key gaps>\"\n" +
                "}";
    }

    private String buildUserPrompt(String jdText, String resumeText) {
        return "JOB DESCRIPTION:\n" + truncate(jdText, 4000) + "\n\nRESUME:\n" + truncate(resumeText, 14000);
    }

    private String truncate(String text, int max) {
        String safeText = nullToEmpty(text);
        return safeText.length() > max ? safeText.substring(0, max) : safeText;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class LlmChatCompletionResponse {
        public List<Choice> choices;
    }

    private static class Choice {
        public Message message;
    }

    private static class Message {
        public String content;
    }
}
