package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationReward;
import com.chandler.motivation.domain.dto.reward.RewardSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationRewardMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationRewardService extends ServiceImpl<MotivationRewardMapper, MotivationReward> {

    private final MotivationChildService childService;
    private final MotivationSystemLogService systemLogService;

    /**
     * 创建奖励，奖励规则会直接进入孩子侧兑换流程。
     */
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
        reward.setRequiredPointType(normalizePointType(request.getRequiredPointType()));
        reward.setRequiredPoints(request.getRequiredPoints() == null ? 0 : request.getRequiredPoints());
        reward.setStockTotal(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setStockRemaining(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setExchangeLimitType(normalizeExchangeLimitType(request.getExchangeLimitType()));
        reward.setExchangeLimitCount(request.getExchangeLimitCount() == null ? 0 : request.getExchangeLimitCount());
        reward.setFulfillmentType(normalizeFulfillmentType(request.getFulfillmentType()));
        reward.setRequireApproval(MotivationConstants.Flag.YES);
        reward.setStatus(MotivationEnums.RewardStatus.ACTIVE.code());
        reward.setDeleted(MotivationConstants.Flag.NO);
        reward.setSortNo(request.getSortNo() == null ? MotivationConstants.Sort.DEFAULT_SORT_NO : request.getSortNo());
        reward.setCreatedByUserId(userId);
        reward.setUpdatedByUserId(userId);
        save(reward);
        systemLogService.recordBusiness(userId, reward.getChildId(), MotivationEnums.LogType.REWARD,
                "创建奖励",
                "创建了奖励「" + reward.getName() + "」，兑换需要 "
                        + reward.getRequiredPoints() + " 个" + pointName(reward.getRequiredPointType()));
        return reward;
    }

    /**
     * 修改奖励的名称、图标、颜色和兑换规则。
     */
    public MotivationReward update(Long rewardId, RewardSaveRequest request, Long userId) {
        MotivationReward reward = getById(rewardId);
        if (reward == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(reward.getDeleted())) {
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
        reward.setRequiredPointType(normalizePointType(request.getRequiredPointType()));
        reward.setRequiredPoints(request.getRequiredPoints() == null ? 0 : request.getRequiredPoints());
        reward.setStockTotal(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setStockRemaining(request.getStockTotal() == null ? 0 : request.getStockTotal());
        reward.setExchangeLimitType(normalizeExchangeLimitType(request.getExchangeLimitType()));
        reward.setExchangeLimitCount(request.getExchangeLimitCount() == null ? 0 : request.getExchangeLimitCount());
        reward.setFulfillmentType(normalizeFulfillmentType(request.getFulfillmentType()));
        reward.setRequireApproval(MotivationConstants.Flag.YES);
        reward.setSortNo(request.getSortNo() == null ? reward.getSortNo() : request.getSortNo());
        reward.setUpdatedByUserId(userId);
        updateById(reward);
        systemLogService.recordBusiness(userId, reward.getChildId(), MotivationEnums.LogType.REWARD,
                "修改奖励", "修改了奖励「" + reward.getName() + "」");
        return reward;
    }

    public List<MotivationReward> listByChild(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return list(new LambdaQueryWrapper<MotivationReward>()
                .eq(MotivationReward::getChildId, childId)
                .eq(MotivationReward::getDeleted, MotivationConstants.Flag.NO)
                .orderByAsc(MotivationReward::getSortNo)
                .orderByDesc(MotivationReward::getUpdateTime));
    }

    /**
     * 软删除奖励，历史兑换记录继续保留。
     */
    public void delete(Long rewardId, Long userId) {
        MotivationReward reward = getById(rewardId);
        if (reward == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(reward.getDeleted())) {
            throw new MotivationException("REWARD_NOT_FOUND", "奖励不存在");
        }
        childService.requireManageAccess(reward.getChildId(), userId);
        reward.setDeleted(MotivationConstants.Flag.YES);
        reward.setStatus(MotivationEnums.RewardStatus.ARCHIVED.code());
        reward.setUpdatedByUserId(userId);
        updateById(reward);
        systemLogService.recordBusiness(userId, reward.getChildId(), MotivationEnums.LogType.REWARD,
                "删除奖励", "删除了奖励「" + reward.getName() + "」");
    }

    /**
     * 校验奖励是否存在且处于可兑换状态。
     */
    public MotivationReward requireActiveReward(Long rewardId, Long userId) {
        MotivationReward reward = getById(rewardId);
        if (reward == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(reward.getDeleted())) {
            throw new MotivationException("REWARD_NOT_FOUND", "奖励不存在");
        }
        childService.requireViewAccess(reward.getChildId(), userId);
        if (!MotivationEnums.codeEquals(MotivationEnums.RewardStatus.ACTIVE, reward.getStatus())) {
            throw new MotivationException("REWARD_NOT_ACTIVE",
                    "奖励状态为「" + MotivationEnums.descriptionOf(MotivationEnums.RewardStatus.class,
                            reward.getStatus(),
                            MotivationEnums.RewardStatus.ARCHIVED) + "」，不能兑换");
        }
        return reward;
    }

    private String normalizeFulfillmentType(String fulfillmentType) {
        MotivationEnums.RewardFulfillmentType resolved = MotivationEnums.fromCode(
                MotivationEnums.RewardFulfillmentType.class,
                fulfillmentType,
                MotivationEnums.RewardFulfillmentType.INVENTORY_DEDUCT);
        return resolved == null ? MotivationEnums.RewardFulfillmentType.INVENTORY_DEDUCT.code() : resolved.code();
    }

    private String normalizePointType(String pointType) {
        MotivationEnums.PointType resolved = MotivationEnums.fromCode(
                MotivationEnums.PointType.class,
                pointType,
                MotivationEnums.PointType.STAR);
        return resolved == null ? MotivationEnums.PointType.STAR.code() : resolved.code();
    }

    private String normalizeExchangeLimitType(String exchangeLimitType) {
        MotivationEnums.ExchangeLimitType resolved = MotivationEnums.fromCode(
                MotivationEnums.ExchangeLimitType.class,
                exchangeLimitType,
                MotivationEnums.ExchangeLimitType.UNLIMITED);
        return resolved == null ? MotivationEnums.ExchangeLimitType.UNLIMITED.code() : resolved.code();
    }

    private String pointName(String pointType) {
        return MotivationEnums.descriptionOf(MotivationEnums.PointType.class, pointType, MotivationEnums.PointType.STAR);
    }
}
