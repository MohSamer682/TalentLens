package com.resumescreener.api.analytics;

import com.resumescreener.api.analytics.dto.AnalyticsOverview;
import com.resumescreener.api.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Set<String> ALLOWED_DAYS = Set.of("30", "90", "365");

    private final AnalyticsRepository analyticsRepository;
    private Clock clock = Clock.systemUTC();

    /** The period ends at the end of today (UTC) and reaches back {@code days} whole days. */
    public AnalyticsOverview overview(String days) {
        if (days == null || !ALLOWED_DAYS.contains(days)) {
            throw ApiException.validation("Choose 30, 90 or 365 days", "Unsupported period: " + days);
        }
        var to = LocalDate.now(clock).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return analyticsRepository.overview(to.minus(Integer.parseInt(days), ChronoUnit.DAYS), to);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
