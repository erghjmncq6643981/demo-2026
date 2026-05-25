package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationReward;
import com.chandler.motivation.domain.dto.reward.RewardSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationRewardMapper;
import com.chandler.motivation.support.MotivationConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationRewardService extends ServiceImpl<MotivationRewardMapper, MotivationReward> {

    private final MotivationChildService childService;
    private final MotivationSystemLogService systemLogService;

    public MotivationReward create(RewardSaveRequest request, Long userId) {
        if (request == null || request.getChildId() == null) {
            throw new MotivationException("CHILD_REQUIRED", "请选择孩子");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new MotivationException("REWARD_NAME_REQUIRED", "奖励名称不能为空");
        }
        childService.requireManageAccess(request.getChildId(), userId);
        MotivationReward reward = new MotivationReward();
        reward.setChildId(request.getChildId());
        reward.setName(request.getName().trim());
        reward.setDescription(request.getDescription());
        reward.setRewardIcon(request.getRewardIcon());
        reward.setRewardColor(request.getRewardColor());
        reward.setRequiredPointType(StringUtils.hasText(request.getRequiredPointType()) ? request.getRequiredPointType() : MotivationConstants.PointType.STAR);
        reward.setRequiredPoints(request.getRequiredPoints() == null ? 0 : request.getRequiredPoints());
        reward.setStockTotal(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setStockRemaining(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setExchangeLimitType(StringUtils.hasText(request.getExchangeLimitType()) ? request.getExchangeLimitType() : MotivationConstants.ExchangeLimitType.UNLIMITED);
        reward.setExchangeLimitCount(request.getExchangeLimitCount() == null ? 0 : request.getExchangeLimitCount());
        reward.setRequireApproval(Boolean.FALSE.equals(request.getRequireApproval()) ? 0 : 1);
        reward.setStatus(MotivationConstants.RewardStatus.ACTIVE);
        reward.setDeleted(0);
        reward.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        reward.setCreatedByUserId(userId);
        reward.setUpdatedByUserId(userId);
        save(reward);
        systemLogService.record(userId, reward.getChildId(), MotivationConstants.LogType.REWARD,
                "创建奖励", "创建奖励「" + reward.getName() + "」，需要 " + reward.getRequiredPoints() + " " + reward.getRequiredPointType());
        return reward;
    }

    public MotivationReward update(Long rewardId, RewardSaveRequest request, Long userId) {
        MotivationReward reward = getById(rewardId);
        if (reward == null || Integer.valueOf(1).equals(reward.getDeleted())) {
            throw new MotivationException("REWARD_NOT_FOUND", "奖励不存在");
        }
        childService.requireManageAccess(reward.getChildId(), userId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new MotivationException("REWARD_NAME_REQUIRED", "奖励名称不能为空");
        }
        reward.setName(request.getName().trim());
        reward.setDescription(request.getDescription());
        reward.setRewardIcon(request.getRewardIcon());
        reward.setRewardColor(request.getRewardColor());
        reward.setRequiredPointType(StringUtils.hasText(request.getRequiredPointType()) ? request.getRequiredPointType() : MotivationConstants.PointType.STAR);
        reward.setRequiredPoints(request.getRequiredPoints() == null ? 0 : request.getRequiredPoints());
        reward.setStockTotal(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setStockRemaining(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setExchangeLimitType(StringUtils.hasText(request.getExchangeLimitType()) ? request.getExchangeLimitType() : MotivationConstants.ExchangeLimitType.UNLIMITED);
        reward.setExchangeLimitCount(request.getExchangeLimitCount() == null ? 0 : request.getExchangeLimitCount());
        reward.setRequireApproval(Boolean.FALSE.equals(request.getRequireApproval()) ? 0 : 1);
        reward.setSortNo(request.getSortNo() == null ? reward.getSortNo() : request.getSortNo());
        reward.setUpdatedByUserId(userId);
        updateById(reward);
        systemLogService.record(userId, reward.getChildId(), MotivationConstants.LogType.REWARD,
                "修改奖励", "修改奖励「" + reward.getName() + "」");
        return reward;
    }

    public List<MotivationReward> listByChild(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return list(new LambdaQueryWrapper<MotivationReward>()
                .eq(MotivationReward::getChildId, childId)
                .eq(MotivationReward::getDeleted, 0)
                .orderByAsc(MotivationReward::getSortNo)
                .orderByDesc(MotivationReward::getUpdateTime));
    }

    public void delete(Long rewardId, Long userId) {
        MotivationReward reward = getById(rewardId);
        if (reward == null || Integer.valueOf(1).equals(reward.getDeleted())) {
            throw new MotivationException("REWARD_NOT_FOUND", "奖励不存在");
        }
        childService.requireManageAccess(reward.getChildId(), userId);
        reward.setDeleted(1);
        reward.setStatus(MotivationConstants.RewardStatus.ARCHIVED);
        reward.setUpdatedByUserId(userId);
        updateById(reward);
        systemLogService.record(userId, reward.getChildId(), MotivationConstants.LogType.REWARD,
                "删除奖励", "删除奖励「" + reward.getName() + "」");
    }

    public MotivationReward requireActiveReward(Long rewardId, Long userId) {
        MotivationReward reward = getById(rewardId);
        if (reward == null || Integer.valueOf(1).equals(reward.getDeleted())) {
            throw new MotivationException("REWARD_NOT_FOUND", "奖励不存在");
        }
        childService.requireViewAccess(reward.getChildId(), userId);
        if (!MotivationConstants.RewardStatus.ACTIVE.equals(reward.getStatus())) {
            throw new MotivationException("REWARD_NOT_ACTIVE", "奖励未启用");
        }
        return reward;
    }
}
