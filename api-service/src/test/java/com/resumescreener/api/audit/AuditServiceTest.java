package com.resumescreener.api.audit;

import com.resumescreener.api.common.RequestIdFilter;
import com.resumescreener.common.audit.AuditEvent;
import com.resumescreener.common.audit.AuditEventRepository;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final AuditEventRepository events = mock(AuditEventRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final AuditService audit = new AuditService(events, users, tx);

    AuditServiceTest() {
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void actorIsTheUserIdNotTheLoginEmail() {
        MDC.put(RequestIdFilter.MDC_USER_ID, "H@x.com");
        when(users.findByEmailIgnoreCase("H@x.com")).thenReturn(Optional.of(User.builder().id("u-1").build()));

        audit.record("ANALYTICS_VIEW", "analytics", null, "success");

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getActorUserId()).isEqualTo("u-1");
    }

    @Test
    void failedWriteIsSwallowedAndRolledBackInItsOwnTransaction() {
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk"));

        assertThatCode(() -> audit.record("X", "t", "1", "success")).doesNotThrowAnyException();
        verify(tx).rollback(any());
    }
}
