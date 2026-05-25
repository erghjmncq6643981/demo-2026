package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationPointLedger;
import com.chandler.motivation.domain.dataobject.MotivationReward;
import com.chandler.motivation.domain.dataobject.MotivationRewardExchange;
import com.chandler.motivation.domain.dto.reward.RewardExchangeConfirmRequest;
import com.chandler.motivation.domain.dto.reward.RewardExchangeRequest;
import com.chandler.motivation.domain.dto.reward.RewardExchangeReviewRequest;
import com.chandler.motivation.domain.dto.reward.RewardFulfillmentRequest;
import com.chandler.motivation.domain.mapper.MotivationRewardExchangeMapper;
import com.chandler.motivation.support.MotivationConstants;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MotivationRewardExchangeService extends ServiceImpl<MotivationRewardExchangeMapper, MotivationRewardExchange> {

    private final MotivationRewardService rewardService;
    private final MotivationPointLedgerService pointLedgerService;
    private final MotivationSystemLogService systemLogService;
    private final MotivationChildService childService;

    public List<MotivationRewardExchange> listByChild(Long childId, Long userId, String status, int limit) {
        childService.requireViewAccess(childId, userId);
        LambdaQueryWrapper<MotivationRewardExchange> wrapper = new LambdaQueryWrapper<MotivationRewardExchange>()
                .eq(MotivationRewardExchange::getChildId, childId)
                .orderByDesc(MotivationRewardExchange::getRequestedAt)
                .orderByDesc(MotivationRewardExchange::getId)
                .last("limit " + Math.max(1, Math.min(limit, 100)));
        if (status != null && !status.isBlank()) {
            wrapper.eq(MotivationRewardExchange::getStatus, status.trim());
        }
        return list(wrapper);
    }

    @Transactional
    public MotivationRewardExchange createRequest(Long userId, RewardExchangeRequest request) {
        if (request == null || request.getRewardId() == null) {
            throw new MotivationException("REWARD_REQUIRED", "请选择奖励");
        }
        MotivationReward reward = rewardService.requireActiveReward(request.getRewardId(), userId);
        validateStock(reward);
        MotivationRewardExchange exchange = new MotivationRewardExchange();
        exchange.setRewardId(reward.getId());
        exchange.setChildId(reward.getChildId());
        exchange.setRewardNameSnapshot(reward.getName());
        exchange.setRewardColorSnapshot(reward.getRewardColor());
        exchange.setRewardIconSnapshot(reward.getRewardIcon());
        exchange.setRequiredPointType(reward.getRequiredPointType());
        exchange.setRequiredPointsSnapshot(reward.getRequiredPoints());
        exchange.setFulfillmentStatus(MotivationConstants.RewardFulfillmentStatus.PENDING);
        exchange.setRemark(request.getRemark());
        exchange.setRequestedByUserId(userId);
        exchange.setRequestedAt(LocalDateTime.now());

        exchange.setStatus(MotivationConstants.RewardExchangeStatus.REQUESTED);
        save(exchange);
        systemLogService.record(userId, reward.getChildId(), MotivationConstants.LogType.REWARD,
                "申请兑换奖励", "申请兑换奖励「" + reward.getName() + "」");
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange approve(Long exchangeId, RewardExchangeReviewRequest request, Long userId) {
        MotivationRewardExchange exchange = requireExchange(exchangeId, userId);
        if (MotivationConstants.RewardExchangeStatus.APPROVED.equals(exchange.getStatus())
                || MotivationConstants.RewardExchangeStatus.COMPLETED.equals(exchange.getStatus())) {
            return exchange;
        }
        if (!MotivationConstants.RewardExchangeStatus.REQUESTED.equals(exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_REQUESTED", "只有待确认兑换可以通过");
        }
        MotivationReward reward = rewardService.requireActiveReward(exchange.getRewardId(), userId);
        validateStock(reward);
        MotivationPointLedger ledger = deductRewardPoints(exchange, userId);
        exchange.setStatus(MotivationConstants.RewardExchangeStatus.APPROVED);
        exchange.setFulfillmentStatus(MotivationConstants.RewardFulfillmentStatus.PENDING);
        exchange.setReviewedByUserId(userId);
        exchange.setReviewedAt(LocalDateTime.now());
        exchange.setDeductedLedgerId(ledger.getId());
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        decreaseStock(reward);
        systemLogService.record(userId, exchange.getChildId(), MotivationConstants.LogType.REWARD,
                "确认奖励兑换", "确认兑换奖励「" + exchange.getRewardNameSnapshot() + "」");
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange reject(Long exchangeId, RewardExchangeReviewRequest request, Long userId) {
        MotivationRewardExchange exchange = requireExchange(exchangeId, userId);
        if (!MotivationConstants.RewardExchangeStatus.REQUESTED.equals(exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_REQUESTED", "只有待确认兑换可以拒绝");
        }
        exchange.setStatus(MotivationConstants.RewardExchangeStatus.REJECTED);
        exchange.setReviewedByUserId(userId);
        exchange.setReviewedAt(LocalDateTime.now());
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.record(userId, exchange.getChildId(), MotivationConstants.LogType.REWARD,
                "拒绝奖励兑换", "拒绝兑换奖励「" + exchange.getRewardNameSnapshot() + "」");
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange updateFulfillment(Long exchangeId, RewardFulfillmentRequest request, Long userId) {
        MotivationRewardExchange exchange = requireManageExchange(exchangeId, userId);
        if (!MotivationConstants.RewardExchangeStatus.APPROVED.equals(exchange.getStatus())
                && !MotivationConstants.RewardExchangeStatus.COMPLETED.equals(exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_APPROVED", "只有已通过兑换可以更新礼物状态");
        }
        String fulfillmentStatus = normalizeFulfillmentStatus(request == null ? null : request.getFulfillmentStatus(), false);
        exchange.setFulfillmentStatus(fulfillmentStatus);
        exchange.setFulfillmentUpdatedByUserId(userId);
        exchange.setFulfillmentUpdatedAt(LocalDateTime.now());
        if (MotivationConstants.RewardFulfillmentStatus.COMPLETED.equals(fulfillmentStatus)) {
            exchange.setCompletedAt(LocalDateTime.now());
        }
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.record(userId, exchange.getChildId(), MotivationConstants.LogType.REWARD,
                "更新礼物状态", "更新奖励「" + exchange.getRewardNameSnapshot() + "」状态为 " + fulfillmentStatus);
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange confirm(Long exchangeId, RewardExchangeConfirmRequest request, Long userId) {
        MotivationRewardExchange exchange = requireViewExchange(exchangeId, userId);
        if (!MotivationConstants.RewardExchangeStatus.APPROVED.equals(exchange.getStatus())
                && !MotivationConstants.RewardExchangeStatus.COMPLETED.equals(exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_APPROVED", "只有已通过兑换可以确认礼物");
        }
        exchange.setStatus(MotivationConstants.RewardExchangeStatus.COMPLETED);
        exchange.setFulfillmentStatus(MotivationConstants.RewardFulfillmentStatus.CONFIRMED);
        exchange.setConfirmedByUserId(userId);
        exchange.setConfirmedAt(LocalDateTime.now());
        exchange.setCompletedAt(LocalDateTime.now());
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.record(userId, exchange.getChildId(), MotivationConstants.LogType.REWARD,
                "确认礼物兑换券", "确认收到奖励「" + exchange.getRewardNameSnapshot() + "」");
        return exchange;
    }

    private MotivationRewardExchange requireExchange(Long exchangeId, Long userId) {
        return requireManageExchange(exchangeId, userId);
    }

    private MotivationRewardExchange requireManageExchange(Long exchangeId, Long userId) {
        MotivationRewardExchange exchange = getById(exchangeId);
        if (exchange == null) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_FOUND", "兑换记录不存在");
        }
        childService.requireManageAccess(exchange.getChildId(), userId);
        return exchange;
    }

    private MotivationRewardExchange requireViewExchange(Long exchangeId, Long userId) {
        MotivationRewardExchange exchange = getById(exchangeId);
        if (exchange == null) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_FOUND", "兑换记录不存在");
        }
        childService.requireViewAccess(exchange.getChildId(), userId);
        return exchange;
    }

    private String normalizeFulfillmentStatus(String fulfillmentStatus, boolean allowConfirmed) {
        String normalized = fulfillmentStatus == null ? "" : fulfillmentStatus.trim().toUpperCase();
        if (allowConfirmed && MotivationConstants.RewardFulfillmentStatus.CONFIRMED.equals(normalized)) {
            return normalized;
        }
        return switch (normalized) {
            case MotivationConstants.RewardFulfillmentStatus.SCHEDULED,
                    MotivationConstants.RewardFulfillmentStatus.IN_PROGRESS,
                    MotivationConstants.RewardFulfillmentStatus.COMPLETED -> normalized;
            default -> MotivationConstants.RewardFulfillmentStatus.PENDING;
        };
    }

    private MotivationPointLedger deductRewardPoints(MotivationRewardExchange exchange, Long userId) {
        int requiredPoints = exchange.getRequiredPointsSnapshot() == null ? 0 : exchange.getRequiredPointsSnapshot();
        if (requiredPoints <= 0) {
            throw new MotivationException("REWARD_POINTS_REQUIRED", "奖励所需积分必须大于 0");
        }
        return pointLedgerService.applyChange(exchange.getChildId(),
                exchange.getRequiredPointType(),
                -requiredPoints,
                MotivationConstants.LedgerSourceType.REWARD_EXCHANGE,
                exchange.getId(),
                exchange.getRewardNameSnapshot(),
                "奖励兑换扣减",
                userId);
    }

    private void validateStock(MotivationReward reward) {
        if (reward.getStockTotal() != null && reward.getStockTotal() > 0
                && (reward.getStockRemaining() == null || reward.getStockRemaining() <= 0)) {
            throw new MotivationException("REWARD_OUT_OF_STOCK", "奖励库存不足");
        }
    }

    private void decreaseStock(MotivationReward reward) {
        if (reward.getStockTotal() == null || reward.getStockTotal() <= 0) {
            return;
        }
        reward.setStockRemaining(Math.max(0, reward.getStockRemaining() - 1));
        rewardService.updateById(reward);
    }
}
