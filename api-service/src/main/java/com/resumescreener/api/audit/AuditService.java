package com.resumescreener.api.audit;

import com.resumescreener.api.common.RequestIdFilter;
import com.resumescreener.common.audit.AuditEvent;
import com.resumescreener.common.audit.AuditEventRepository;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    // Own transaction: callers are mostly @Transactional, and an insert that fails at flush must not poison theirs
    private final TransactionTemplate ownTransaction;

    public AuditService(AuditEventRepository auditEventRepository, UserRepository userRepository,
                        PlatformTransactionManager transactionManager) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(String action, String targetType, String targetId, String outcome, String details) {
        AuditEvent event = AuditEvent.builder()
                .occurredAt(Instant.now())
                .actorUserId(actorId())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .requestId(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                .outcome(outcome)
                .details(details)
                .build();
        try {
            ownTransaction.executeWithoutResult(tx -> auditEventRepository.saveAndFlush(event));
        } catch (RuntimeException e) {
            // A failed audit write must not fail the audited request; the log line below still carries it
            log.error("audit write failed action={} target={}:{}", action, targetType, targetId, e);
        }
        log.info("audit=true action={} target={}:{} outcome={}", action, targetType, targetId, outcome);
    }

    public void record(String action, String targetType, String targetId, String outcome) {
        record(action, targetType, targetId, outcome, null);
    }

    /** MDC holds the login email, but audit_events.actor_user_id is a FK to users.id. */
    private String actorId() {
        String email = MDC.get(RequestIdFilter.MDC_USER_ID);
        return email == null ? null : userRepository.findByEmailIgnoreCase(email).map(User::getId).orElse(null);
    }
}
