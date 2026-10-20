package com.resumescreener.api.portal;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps calls to the public access-link endpoints per client address, so tokens can't be guessed at speed.
 * The counts live in this instance only; behind more than one API instance this belongs in the gateway.
 */
public class InviteThrottle extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED = 10_000;

    private record Window(Instant start, int count) {
    }

    private final int perMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public InviteThrottle(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/portal/invites/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        if (windows.size() > MAX_TRACKED) {
            windows.values().removeIf(w -> w.start().plus(WINDOW).isBefore(now));
        }
        Window window = windows.compute(request.getRemoteAddr(), (ip, w) ->
                w == null || !now.isBefore(w.start().plus(WINDOW)) ? new Window(now, 1) : new Window(w.start(), w.count() + 1));
        if (window.count() > perMinute) {
            response.setHeader("Retry-After", String.valueOf(Duration.between(now, window.start().plus(WINDOW)).toSeconds() + 1));
            ErrorResponses.write(response, HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMITED,
                    "Too many attempts. Wait a minute and try again.", "Access-link requests are limited per address");
            return;
        }
        chain.doFilter(request, response);
    }
}
