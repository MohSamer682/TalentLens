package com.resumescreener.api.admin;

import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.common.audit.AuditEvent;
import com.resumescreener.common.audit.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuditEventRepository auditEventRepository;

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<Page<AuditEvent>>> getAuditLog(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            Pageable pageable) {
        Page<AuditEvent> data = auditEventRepository.findFiltered(userId, action, pageable);
        return ResponseEntity.ok(ApiResponse.success("Audit log loaded", data));
    }
}
