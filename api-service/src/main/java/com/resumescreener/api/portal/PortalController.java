package com.resumescreener.api.portal;

import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.portal.dto.ApplicationSummary;
import com.resumescreener.api.portal.dto.ApplicationView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** A candidate's own applications. The URL rule in SecurityConfig limits {@code /api/portal/**} to candidates. */
@RestController
@RequestMapping("/api/portal/applications")
@RequiredArgsConstructor
public class PortalController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PortalQueryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ApplicationSummary>>> list(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size,
                                                                      Authentication auth) {
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ResponseEntity.ok(ApiResponse.success("Applications", service.applications(auth.getName(), request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApplicationView>> get(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Application", service.application(auth.getName(), id)));
    }
}
