package com.chandler.learning.agent.system.application;

import com.chandler.learning.agent.system.domain.entity.LearningSystemLog;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLogOutbox;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogMapper;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemLogOutboxPersistenceServiceTest {

    @Test
    void atomicallyClaimsAndBatchPersistsOutboxRows() {
        LearningSystemLogOutboxMapper outboxMapper = mock(LearningSystemLogOutboxMapper.class);
        LearningSystemLogMapper systemLogMapper = mock(LearningSystemLogMapper.class);
        SystemLogOutboxPersistenceService service = new SystemLogOutboxPersistenceService(outboxMapper, systemLogMapper);
        LearningSystemLogOutbox outbox = outbox();
        when(outboxMapper.claimByIds(eq(List.of(9001L)), anyString())).thenReturn(1);
        when(outboxMapper.selectByClaimToken(anyString())).thenReturn(List.of(outbox));

        service.persistByIds(List.of(9001L));

        ArgumentCaptor<List<LearningSystemLog>> logs = ArgumentCaptor.forClass(List.class);
        verify(systemLogMapper).insertBatch(logs.capture());
        LearningSystemLog log = logs.getValue().get(0);
        assertThat(log.getId()).isEqualTo(9001L);
        assertThat(log.getUserId()).isEqualTo(1001L);
        assertThat(log.getTitle()).isEqualTo("创建学习计划");
        assertThat(log.getCreateTime()).isEqualTo(outbox.getOccurredAt());
        verify(outboxMapper).markSucceededByClaimToken(anyString());
    }

    private LearningSystemLogOutbox outbox() {
        LearningSystemLogOutbox outbox = new LearningSystemLogOutbox();
        outbox.setId(9001L);
        outbox.setUserId(1001L);
        outbox.setLogType("learning_plan");
        outbox.setTitle("创建学习计划");
        outbox.setDetail("雅思");
        outbox.setSource("server");
        outbox.setOccurredAt(LocalDateTime.of(2026, 8, 28, 10, 30));
        outbox.setCreateBy(1001L);
        outbox.setUpdateBy(1001L);
        outbox.setUpdateTime(LocalDateTime.of(2026, 8, 28, 10, 30));
        return outbox;
    }
}
