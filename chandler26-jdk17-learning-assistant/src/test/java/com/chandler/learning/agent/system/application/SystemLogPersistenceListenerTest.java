package com.chandler.learning.agent.system.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemLogPersistenceListenerTest {

    @Test
    void delegatesCommittedEventToOutboxPersistence() {
        SystemLogOutboxPersistenceService persistenceService = mock(SystemLogOutboxPersistenceService.class);
        SystemLogPersistenceListener listener = new SystemLogPersistenceListener(persistenceService);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 28, 10, 30);

        listener.persist(new SystemLogRecordedEvent(9001L, 1001L, "learning_plan", "创建计划", "雅思", "server",
                "plan", "2001", occurredAt, "trace-1"));

        verify(persistenceService).persistByIds(List.of(9001L));
    }

    @Test
    void ignoresPersistenceFailureSoCompletedBusinessIsNotReverted() {
        SystemLogOutboxPersistenceService persistenceService = mock(SystemLogOutboxPersistenceService.class);
        doThrow(new IllegalStateException("database unavailable")).when(persistenceService)
                .persistByIds(List.of(9001L));
        SystemLogPersistenceListener listener = new SystemLogPersistenceListener(persistenceService);

        assertThatCode(() -> listener.persist(new SystemLogRecordedEvent(9001L, 1001L, "auth", "登录成功", null,
                "server", null, null, LocalDateTime.now(), "trace-1"))).doesNotThrowAnyException();
    }
}
