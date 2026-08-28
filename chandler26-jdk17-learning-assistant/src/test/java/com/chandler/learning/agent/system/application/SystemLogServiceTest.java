package com.chandler.learning.agent.system.application;

import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLogOutbox;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogMapper;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SystemLogServiceTest {

    @Test
    void publishesNormalizedServerEventWithoutSynchronouslyWritingDatabase() {
        LearningSystemLogMapper mapper = mock(LearningSystemLogMapper.class);
        LearningSystemLogOutboxMapper outboxMapper = outboxMapperWithAssignedId();
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SystemLogService service = new SystemLogService(mapper, outboxMapper, eventPublisher,
                mock(CurrentUserContext.class));

        service.record(1001L, SystemLogType.AUTH, " 登录成功 ", " chandler ");

        ArgumentCaptor<SystemLogRecordedEvent> captor = ArgumentCaptor.forClass(SystemLogRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        verifyNoInteractions(mapper);
        SystemLogRecordedEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(1001L);
        assertThat(event.outboxId()).isEqualTo(9001L);
        assertThat(event.logType()).isEqualTo(SystemLogType.AUTH.getCode());
        assertThat(event.title()).isEqualTo("登录成功");
        assertThat(event.detail()).isEqualTo("chandler");
        assertThat(event.source()).isEqualTo("server");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void forcesClientSourceAndBoundsClientPayload() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SystemLogService service = new SystemLogService(mock(LearningSystemLogMapper.class), outboxMapperWithAssignedId(),
                eventPublisher, mock(CurrentUserContext.class));
        String detail = "x".repeat(8_100);

        service.recordClient(1002L, "unknown", "客户端事件", detail, " article ", " 123 ");

        ArgumentCaptor<SystemLogRecordedEvent> captor = ArgumentCaptor.forClass(SystemLogRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        SystemLogRecordedEvent event = captor.getValue();
        assertThat(event.logType()).isEqualTo(SystemLogType.SYSTEM.getCode());
        assertThat(event.source()).isEqualTo("client");
        assertThat(event.detail()).hasSize(8_000);
        assertThat(event.businessType()).isEqualTo("article");
        assertThat(event.businessId()).isEqualTo("123");
    }

    private LearningSystemLogOutboxMapper outboxMapperWithAssignedId() {
        LearningSystemLogOutboxMapper outboxMapper = mock(LearningSystemLogOutboxMapper.class);
        doAnswer(invocation -> {
            LearningSystemLogOutbox outbox = invocation.getArgument(0);
            outbox.setId(9001L);
            return 1;
        }).when(outboxMapper).insert(org.mockito.ArgumentMatchers.any(LearningSystemLogOutbox.class));
        return outboxMapper;
    }
}
