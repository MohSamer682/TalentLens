package com.resumescreener.api.analytics;

import com.resumescreener.api.analytics.dto.AnalyticsOverview;
import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuditService auditService;

    // days is a String so "abc" gets the same message as 7
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('ANALYTICS_VIEW')")
    public ResponseEntity<ApiResponse<AnalyticsOverview>> overview(@RequestParam(defaultValue = "90") String days) {
        AnalyticsOverview data = analyticsService.overview(days);
        auditService.record("ANALYTICS_VIEW", "analytics", null, "success");
        return ResponseEntity.ok(ApiResponse.success("Analytics loaded", data));
    }
}
