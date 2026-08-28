package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 异步持久化学习单元切换与审计日志，解放接口请求线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningUnitStartedListener {

    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    @Async("learningEventExecutor")
    @EventListener
    public void onUnitStarted(LearningUnitStartedEvent event) {
        try {
            // 1. 如果切换自其他进行中的单元，将旧单元状态重置为 ready
            if (event.previousUnitId() != null && !Objects.equals(event.previousUnitId(), event.unitId())) {
                LearningPlanUnit current = unitMapper.selectById(event.previousUnitId());
                if (current != null && ScenePlanConstants.UNIT_IN_PROGRESS.equals(current.getStatus())) {
                    current.setStatus(ScenePlanConstants.UNIT_READY);
                    current.setUpdateTime(event.startedTime());
                    unitMapper.updateById(current);
                }
            }

            // 2. 更新当前单元为 in_progress
            LearningPlanUnit unit = unitMapper.selectById(event.unitId());
            if (unit != null) {
                unit.setStatus(ScenePlanConstants.UNIT_IN_PROGRESS);
                if (event.firstStart() && unit.getStartedTime() == null) {
                    unit.setStartedTime(event.startedTime());
                }
                unit.setUpdateTime(event.startedTime());
                unitMapper.updateById(unit);
            }

            // 3. 更新计划的 currentUnitId
            LearningPlan plan = planMapper.selectById(event.planId());
            if (plan != null) {
                plan.setCurrentUnitId(event.unitId());
                plan.setUpdateTime(event.startedTime());
                planMapper.updateById(plan);
            }

            // 4. 记录系统审计日志与业务日志
            systemLogService.record(event.userId(), SystemLogType.LEARNING_PLAN, "切换场景学习单元",
                    event.planName() + " / " + event.unitTitle());
            log.info("用户「{}」开始学习计划「{}」中的场景「{}」",
                    userDisplayNameService.userName(event.userId()), event.planName(), event.unitTitle());
        } catch (RuntimeException ex) {
            log.error("event=unit_started_async_update result=failed planId={} unitId={} error={}",
                    event.planId(), event.unitId(), ex.getMessage(), ex);
        }
    }
}
