package com.resumescreener.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final int BODY_LOG_LIMIT = 500;

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        if (!request.getRequestURI().startsWith("/api/")) {
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.clear();
            }
            return;
        }
        // Responses here are small JSON (downloads are presigned URLs), so buffering them is cheap
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, wrapped);
        } finally {
            logExchange(request, wrapped, (System.nanoTime() - start) / 1_000_000);
            wrapped.copyBodyToResponse();
            MDC.clear();
        }
    }

    /** One line per API call; the body is added only for failures, truncated, so secrets and PII in 2xx payloads stay out of logs. */
    private void logExchange(HttpServletRequest request, ContentCachingResponseWrapper response, long ms) {
        int status = response.getStatus();
        String query = request.getQueryString() == null ? "" : "?" + flat(request.getQueryString());
        String line = "{} {}{} -> {} {} {}ms";
        Object[] args = {request.getMethod(), flat(request.getRequestURI()), query, status, response.getContentType(), ms};
        if (status < 400) {
            log.info(line, args);
            return;
        }
        String body = flat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        if (body.length() > BODY_LOG_LIMIT) {
            body = body.substring(0, BODY_LOG_LIMIT) + "...";
        }
        Object[] withBody = java.util.Arrays.copyOf(args, args.length + 1);
        withBody[args.length] = body;
        log.warn(line + " body={}", withBody);
    }

    /** Request text is attacker-controlled; line breaks would let it forge log lines. */
    private static String flat(String value) {
        return value.replaceAll("\\p{Cntrl}", " ");
    }
}
