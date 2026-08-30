package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.api.request.LearningPlanUpdateRequest;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 学习计划的编辑、暂停、恢复和取消等生命周期状态转换。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanLifecycleService {

    private final LearningPlanMapper planMapper;
    private final WordbookService wordbookService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /** 更新计划基础信息与状态，并告知调用方是否需要在事务提交后生成首个场景。 */
    public UpdateOutcome update(Long userId, LearningPlan plan, LearningPlanUpdateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        boolean generateFirstUnit = false;
        plan.setName(request.getName().trim());
        plan.setLearningPurpose(StringUtils.hasText(request.getLearningPurpose())
                ? request.getLearningPurpose().trim() : null);
        plan.setStartTime(request.getStartTime());
        plan.setEndTime(request.getEndTime());
        if (request.getWordbookId() != null && !request.getWordbookId().equals(plan.getWordbookId())) {
            plan.setWordbookId(wordbookService.requireOwnedWordbook(userId, request.getWordbookId()).getId());
        }
        if (request.getStatus() != null && !request.getStatus().trim().equals(plan.getStatus())) {
            String newStatus = request.getStatus().trim();
            if (ScenePlanConstants.STATUS_ACTIVE.equals(newStatus)) {
                if (!ScenePlanConstants.STATUS_PAUSED.equals(plan.getStatus())
                        && !ScenePlanConstants.STATUS_NOT_STARTED.equals(plan.getStatus())) {
                    throw stateError("只有暂停或未开始的计划才可以恢复/启动");
                }
                plan.setStatus(ScenePlanConstants.STATUS_ACTIVE);
                generateFirstUnit = plan.getCurrentUnitId() == null;
            } else if (ScenePlanConstants.STATUS_PAUSED.equals(newStatus)) {
                if (!ScenePlanConstants.STATUS_ACTIVE.equals(plan.getStatus())) {
                    throw stateError("只有进行中的计划才可以暂停");
                }
                plan.setStatus(ScenePlanConstants.STATUS_PAUSED);
            } else if (ScenePlanConstants.STATUS_CANCELLED.equals(newStatus)) {
                ensureNotTerminal(plan, "已完成或已取消的计划无法取消");
                plan.setStatus(ScenePlanConstants.STATUS_CANCELLED);
            } else if (ScenePlanConstants.STATUS_NOT_STARTED.equals(newStatus)) {
                ensureNotTerminal(plan, "已完成或已取消的计划无法设为未开始");
                plan.setStatus(ScenePlanConstants.STATUS_NOT_STARTED);
            } else if (ScenePlanConstants.STATUS_COMPLETED.equals(newStatus)) {
                plan.setStatus(ScenePlanConstants.STATUS_COMPLETED);
            } else {
                throw stateError("无效的学习计划状态: " + newStatus);
            }
        }
        plan.setUpdateTime(now);
        planMapper.updateById(plan);
        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "修改场景学习计划",
                plan.getName() + "，状态: " + plan.getStatus());
        log.info("用户「{}」修改了场景学习计划「{}」，状态 = {}",
                userDisplayNameService.userName(userId), plan.getName(), plan.getStatus());
        return new UpdateOutcome(generateFirstUnit);
    }

    /** 暂停场景学习计划。 */
    public void pause(Long userId, LearningPlan plan) {
        if (!ScenePlanConstants.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw stateError("只有进行中的计划才可以暂停");
        }
        plan.setStatus(ScenePlanConstants.STATUS_PAUSED);
        persistAndLog(userId, plan, "暂停场景学习计划");
    }

    /** 恢复场景学习计划。 */
    public void resume(Long userId, LearningPlan plan) {
        if (!ScenePlanConstants.STATUS_PAUSED.equals(plan.getStatus())
                && !ScenePlanConstants.STATUS_NOT_STARTED.equals(plan.getStatus())) {
            throw stateError("只有暂停或未开始的计划才可以恢复/启动");
        }
        plan.setStatus(ScenePlanConstants.STATUS_ACTIVE);
        persistAndLog(userId, plan, "恢复场景学习计划");
    }

    /** 取消场景学习计划。 */
    public void cancel(Long userId, LearningPlan plan) {
        ensureNotTerminal(plan, "已完成或已取消的计划无法取消");
        plan.setStatus(ScenePlanConstants.STATUS_CANCELLED);
        persistAndLog(userId, plan, "取消场景学习计划");
    }

    private void persistAndLog(Long userId, LearningPlan plan, String action) {
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, action, plan.getName());
        log.info("用户「{}」{}了场景学习计划「{}」", userDisplayNameService.userName(userId),
                action.replace("场景学习计划", ""), plan.getName());
    }

    private void ensureNotTerminal(LearningPlan plan, String message) {
        if (ScenePlanConstants.STATUS_COMPLETED.equals(plan.getStatus())
                || ScenePlanConstants.STATUS_CANCELLED.equals(plan.getStatus())) {
            throw stateError(message);
        }
    }

    private LearningAssistantException stateError(String message) {
        return LearningAssistantException.badRequest(
                LearningErrorCode.LEARNING_PLAN_STATE_ERROR, message);
    }

    public record UpdateOutcome(boolean generateFirstUnit) {
    }
}
