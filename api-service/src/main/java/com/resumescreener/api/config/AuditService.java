package com.resumescreener.api.config;

import com.resumescreener.common.entity.AuditEvent;
import com.resumescreener.common.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public void record(String action, String targetType, String targetId, String outcome, String details) {
        AuditEvent event = AuditEvent.builder()
                .occurredAt(Instant.now())
                .actorUserId(MDC.get(RequestIdFilter.MDC_USER_ID))
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .requestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .outcome(outcome)
                .details(details)
                .build();
        auditEventRepository.save(event);
        log.info("audit=true action={} target={}:{} outcome={}", action, targetType, targetId, outcome);
    }

    public void record(String action, String targetType, String targetId, String outcome) {
        record(action, targetType, targetId, outcome, null);
    }
}
