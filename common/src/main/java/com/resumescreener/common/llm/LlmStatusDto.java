package com.resumescreener.common.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Which LLM model the batch worker scores with, and whether the provider answered a health probe.
 * The API key and full base URL never leave the worker; only the provider host is reported.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmStatusDto {

    public enum State {
        /** Provider reachable and the configured model is listed by it */
        AVAILABLE,
        /** Provider reachable, but it does not list models, so the model could not be confirmed */
        REACHABLE,
        /** Provider reachable, but the configured model is not in its model list */
        MODEL_NOT_FOUND,
        /** Provider rejected the API key */
        AUTH_FAILED,
        /** Provider could not be reached, timed out or returned a server error */
        UNREACHABLE,
        /** API key, model or base URL is missing in the worker configuration */
        NOT_CONFIGURED,
        /** The API could not reach the batch worker to ask */
        UNKNOWN
    }

    private String model;
    private String provider;
    private State state;
    private boolean available;
    private String message;
    private Instant checkedAt;
}
