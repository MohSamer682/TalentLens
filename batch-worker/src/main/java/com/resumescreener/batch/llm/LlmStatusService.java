package com.resumescreener.batch.llm;

import com.resumescreener.common.llm.LlmStatusDto;
import com.resumescreener.common.llm.LlmStatusDto.State;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Probes the configured OpenAI-compatible provider with GET /models. The result is cached briefly so
 * opening sessions in the UI does not turn into a provider call per page view.
 */
@Service
@Slf4j
public class LlmStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.base-url}")
    private String baseUrl;

    private final RestClient probeClient;

    private volatile LlmStatusDto cached;

    public LlmStatusService(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PROBE_TIMEOUT);
        factory.setReadTimeout(PROBE_TIMEOUT);
        this.probeClient = builder.clone().requestFactory(factory).build();
    }

    public LlmStatusDto getStatus() {
        LlmStatusDto current = cached;
        if (current != null && current.getCheckedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return current;
        }
        LlmStatusDto fresh = probe();
        cached = fresh;
        return fresh;
    }

    private LlmStatusDto probe() {
        String trimmedModel = isBlank(model) ? null : model.trim();
        String provider = providerHost();
        if (isBlank(apiKey) || trimmedModel == null || isBlank(baseUrl)) {
            return status(trimmedModel, provider, State.NOT_CONFIGURED,
                    "LLM API key, model or base URL is not set on the batch worker");
        }

        try {
            ModelList models = probeClient.get()
                    .uri(normalizedBaseUrl() + "/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                    .retrieve()
                    .body(ModelList.class);

            if (models == null || models.data == null || models.data.isEmpty()) {
                return status(trimmedModel, provider, State.REACHABLE,
                        "Provider is reachable but did not return a model list");
            }
            boolean listed = models.data.stream()
                    .anyMatch(m -> m != null && trimmedModel.equals(m.id));
            return listed
                    ? status(trimmedModel, provider, State.AVAILABLE, "Model is available")
                    : status(trimmedModel, provider, State.MODEL_NOT_FOUND,
                            "Provider does not list model " + trimmedModel);
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                return status(trimmedModel, provider, State.AUTH_FAILED,
                        "Provider rejected the API key (HTTP " + code + ")");
            }
            // Some OpenAI-compatible providers do not implement /models; the server still answered
            return status(trimmedModel, provider, State.REACHABLE,
                    "Provider is reachable but the model list is unavailable (HTTP " + code + ")");
        } catch (RestClientResponseException e) {
            log.warn("LLM status probe failed: HTTP {}", e.getStatusCode().value());
            return status(trimmedModel, provider, State.UNREACHABLE,
                    "Provider returned HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("LLM status probe failed: {}", e.getMessage());
            return status(trimmedModel, provider, State.UNREACHABLE, "Provider could not be reached");
        }
    }

    private static LlmStatusDto status(String model, String provider, State state, String message) {
        boolean available = state == State.AVAILABLE || state == State.REACHABLE;
        return new LlmStatusDto(model, provider, state, available, message, Instant.now());
    }

    private String providerHost() {
        if (isBlank(baseUrl)) {
            return null;
        }
        try {
            String host = URI.create(baseUrl.trim()).getHost();
            return host != null ? host : baseUrl.trim();
        } catch (IllegalArgumentException e) {
            return null;
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class ModelList {
        public List<ModelEntry> data;
    }

    private static class ModelEntry {
        public String id;
    }
}
