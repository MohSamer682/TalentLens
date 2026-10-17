package com.resumescreener.api.llm;

import com.resumescreener.common.llm.LlmStatusDto;
import com.resumescreener.common.llm.LlmStatusDto.State;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Asks the batch worker which LLM model it scores with and whether the provider is up. The worker
 * owns the LLM configuration and caches its probe, so this call is cheap. A worker that does not
 * answer is reported as UNKNOWN rather than failing the session request.
 */
@Service
@Slf4j
public class LlmStatusClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final RestClient client;

    @Value("${batch-worker.url}")
    private String batchWorkerUrl;

    @Value("${batch-worker.shared-secret}")
    private String sharedSecret;

    public LlmStatusClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(TIMEOUT);
        this.client = builder.clone().requestFactory(factory).build();
    }

    public LlmStatusDto getStatus() {
        try {
            LlmStatusDto status = client.get()
                    .uri(batchWorkerUrl + "/internal/llm/status")
                    .header("X-Internal-Secret", sharedSecret)
                    .header("X-Request-Id", MDC.get("requestId"))
                    .retrieve()
                    .body(LlmStatusDto.class);
            if (status != null) {
                return status;
            }
        } catch (Exception e) {
            log.warn("Could not read LLM status from batch worker: {}", e.getMessage());
        }
        return new LlmStatusDto(null, null, State.UNKNOWN, false,
                "Batch worker did not report LLM status", Instant.now());
    }
}
