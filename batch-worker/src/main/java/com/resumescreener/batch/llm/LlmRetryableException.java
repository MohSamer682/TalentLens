package com.resumescreener.batch.llm;

/** Transient LLM failure (rate limit, malformed output) that is worth another attempt. */
public class LlmRetryableException extends LlmException {
    public LlmRetryableException(String message) {
        super(message);
    }

    public LlmRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
