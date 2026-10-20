package com.resumescreener.api.auth;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Failed sign-ins per email, counted across both doors: the sixth inside fifteen minutes is refused until the window passes.
 * The counts live in this instance only, the same shortcut as the access-link throttle.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_TRACKED = 10_000;

    private record Failures(Instant first, int count) {
    }

    private final Clock clock;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private final int maxTracked;

    @org.springframework.beans.factory.annotation.Autowired
    public LoginThrottle(Clock clock) {
        this(clock, MAX_TRACKED);
    }

    LoginThrottle(Clock clock, int maxTracked) {
        this.clock = clock;
        this.maxTracked = maxTracked;
    }

    int tracked() {
        return failures.size();
    }

    public void check(String email) {
        Failures seen = failures.get(key(email));
        if (seen != null && seen.count() >= MAX_FAILURES && clock.instant().isBefore(seen.first().plus(WINDOW))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMITED,
                    "Too many attempts. Try again in a few minutes.", "Sign-in is locked for this email");
        }
    }

    public void failed(String email) {
        Instant now = clock.instant();
        if (failures.size() >= maxTracked && !failures.containsKey(key(email))) failures.values().removeIf(f -> !now.isBefore(f.first().plus(WINDOW)));
        // Still full of live windows: drop the oldest, so a flood of made-up emails can't stop counting for a real one
        while (failures.size() >= maxTracked && !failures.containsKey(key(email))) {
            failures.entrySet().stream().min(Map.Entry.comparingByValue(java.util.Comparator.comparing(Failures::first)))
                    .ifPresent(oldest -> failures.remove(oldest.getKey()));
        }
        failures.merge(key(email), new Failures(now, 1),
                (old, fresh) -> now.isBefore(old.first().plus(WINDOW)) ? new Failures(old.first(), old.count() + 1) : fresh);
    }

    public void succeeded(String email) {
        failures.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
