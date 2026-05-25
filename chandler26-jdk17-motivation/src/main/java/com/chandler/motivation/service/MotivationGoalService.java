package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationGoal;
import com.chandler.motivation.domain.dto.goal.GoalSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationGoalMapper;
import com.chandler.motivation.support.MotivationConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationGoalService extends ServiceImpl<MotivationGoalMapper, MotivationGoal> {

    private final MotivationChildService childService;
    private final MotivationSystemLogService systemLogService;

    public MotivationGoal create(GoalSaveRequest request, Long userId) {
        if (request == null || request.getChildId() == null) {
            throw new MotivationException("CHILD_REQUIRED", "请选择孩子");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new MotivationException("GOAL_NAME_REQUIRED", "目标名称不能为空");
        }
        childService.requireManageAccess(request.getChildId(), userId);
        MotivationGoal goal = new MotivationGoal();
        goal.setChildId(request.getChildId());
        goal.setName(request.getName().trim());
        goal.setDescription(request.getDescription());
        goal.setGoalColor(request.getGoalColor());
        goal.setIcon(request.getIcon());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        goal.setTargetPoints(request.getTargetPoints() == null ? 0 : request.getTargetPoints());
        goal.setStatus(MotivationConstants.GoalStatus.ACTIVE);
        goal.setDeleted(0);
        goal.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        goal.setCreatedByUserId(userId);
        goal.setUpdatedByUserId(userId);
        save(goal);
        systemLogService.record(userId, goal.getChildId(), MotivationConstants.LogType.TASK,
                "创建成长目标", "创建目标「" + goal.getName() + "」");
        return goal;
    }

    public List<MotivationGoal> listByChild(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return list(new LambdaQueryWrapper<MotivationGoal>()
                .eq(MotivationGoal::getChildId, childId)
                .eq(MotivationGoal::getDeleted, 0)
                .orderByAsc(MotivationGoal::getSortNo)
                .orderByDesc(MotivationGoal::getUpdateTime));
    }

    public MotivationGoal update(Long goalId, GoalSaveRequest request, Long userId) {
        MotivationGoal goal = getById(goalId);
        if (goal == null || Integer.valueOf(1).equals(goal.getDeleted())) {
            throw new MotivationException("GOAL_NOT_FOUND", "目标不存在");
        }
        childService.requireManageAccess(goal.getChildId(), userId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new MotivationException("GOAL_NAME_REQUIRED", "目标名称不能为空");
        }
        goal.setName(request.getName().trim());
        goal.setDescription(request.getDescription());
        goal.setGoalColor(request.getGoalColor());
        goal.setIcon(request.getIcon());
        goal.setStartDate(request.getStartDate());
        goal.setEndDate(request.getEndDate());
        goal.setTargetPoints(request.getTargetPoints() == null ? 0 : request.getTargetPoints());
        goal.setSortNo(request.getSortNo() == null ? goal.getSortNo() : request.getSortNo());
        goal.setUpdatedByUserId(userId);
        updateById(goal);
        systemLogService.record(userId, goal.getChildId(), MotivationConstants.LogType.TASK,
                "修改成长目标", "修改目标「" + goal.getName() + "」");
        return goal;
    }

    public void delete(Long goalId, Long userId) {
        MotivationGoal goal = getById(goalId);
        if (goal == null || Integer.valueOf(1).equals(goal.getDeleted())) {
            throw new MotivationException("GOAL_NOT_FOUND", "目标不存在");
        }
        childService.requireManageAccess(goal.getChildId(), userId);
        goal.setDeleted(1);
        goal.setStatus(MotivationConstants.GoalStatus.PAUSED);
        goal.setUpdatedByUserId(userId);
        updateById(goal);
        systemLogService.record(userId, goal.getChildId(), MotivationConstants.LogType.TASK,
                "删除成长目标", "删除目标「" + goal.getName() + "」");
    }

    public MotivationGoal requireActiveGoal(Long goalId, Long childId) {
        MotivationGoal goal = getById(goalId);
        if (goal == null || !childId.equals(goal.getChildId()) || Integer.valueOf(1).equals(goal.getDeleted())) {
            throw new MotivationException("GOAL_NOT_FOUND", "目标不存在");
        }
        return goal;
    }
}
