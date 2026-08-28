package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
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
    private LearningPlanMapper planMapper;
    @Mock
    private LearningPlanUnitMapper unitMapper;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private UserDisplayNameService userDisplayNameService;

    @InjectMocks
    private LearningUnitStartedListener listener;

    @Test
    @DisplayName("异步监听器正确重置前一单元状态并更新当前单元与计划")
    void shouldHandleUnitStartedEventCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        Long userId = 1001L;
        Long planId = 2001L;
        Long previousUnitId = 3001L;
        Long currentUnitId = 3002L;

        LearningPlanUnit previousUnit = new LearningPlanUnit();
        previousUnit.setId(previousUnitId);
        previousUnit.setStatus(ScenePlanConstants.UNIT_IN_PROGRESS);

        LearningPlanUnit currentUnit = new LearningPlanUnit();
        currentUnit.setId(currentUnitId);
        currentUnit.setStatus(ScenePlanConstants.UNIT_READY);

        LearningPlan plan = new LearningPlan();
        plan.setId(planId);
        plan.setCurrentUnitId(previousUnitId);

        when(unitMapper.selectById(previousUnitId)).thenReturn(previousUnit);
        when(unitMapper.selectById(currentUnitId)).thenReturn(currentUnit);
        when(planMapper.selectById(planId)).thenReturn(plan);
        when(userDisplayNameService.userName(userId)).thenReturn("测试用户");

        LearningUnitStartedEvent event = new LearningUnitStartedEvent(
                userId, planId, currentUnitId, previousUnitId, true, now,
                "测试计划", "测试场景", "test-trace-id");

        listener.onUnitStarted(event);

        verify(unitMapper).updateById(previousUnit);
        verify(unitMapper).updateById(currentUnit);
        verify(planMapper).updateById(plan);
        verify(systemLogService).record(eq(userId), eq(SystemLogType.LEARNING_PLAN), eq("切换场景学习单元"), eq("测试计划 / 测试场景"));
    }
}
