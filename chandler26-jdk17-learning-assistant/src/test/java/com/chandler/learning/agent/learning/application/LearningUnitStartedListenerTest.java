package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningUnitStartedListenerTest {

    @Mock
    private SystemLogService systemLogService;
    @Mock
    private UserDisplayNameService userDisplayNameService;

    @InjectMocks
    private LearningUnitStartedListener listener;

    @Test
    @DisplayName("事务提交后异步监听器记录场景切换审计日志")
    void shouldHandleUnitStartedEventCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 1001L;
        Long planId = 2001L;
        Long previousUnitId = 3001L;
        Long currentUnitId = 3002L;

        when(userDisplayNameService.userName(userId)).thenReturn("测试用户");

        LearningUnitStartedEvent event = new LearningUnitStartedEvent(
                userId, planId, currentUnitId, previousUnitId, true, now,
                "测试计划", "测试场景", "test-trace-id");

        listener.onUnitStarted(event);

        verify(systemLogService).record(eq(userId), eq(SystemLogType.LEARNING_PLAN), eq("切换场景学习单元"), eq("测试计划 / 测试场景"));
    }
}
