package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationPointLedger;
import com.chandler.motivation.domain.dataobject.MotivationReward;
import com.chandler.motivation.domain.dataobject.MotivationRewardExchange;
import com.chandler.motivation.domain.dto.points.PointExchangeRuleResponse;
import com.chandler.motivation.domain.dto.reward.RewardExchangeConfirmRequest;
import com.chandler.motivation.domain.dto.reward.RewardExchangeRequest;
import com.chandler.motivation.domain.dto.reward.RewardExchangeReviewRequest;
import com.chandler.motivation.domain.dto.reward.RewardFulfillmentRequest;
import com.chandler.motivation.domain.mapper.MotivationRewardExchangeMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationRewardExchangeService extends ServiceImpl<MotivationRewardExchangeMapper, MotivationRewardExchange> {

    private static final long REFUND_SOURCE_OFFSET = 1_000_000_000L;

    private final MotivationRewardService rewardService;
    private final MotivationPointLedgerService pointLedgerService;
    private final MotivationSystemLogService systemLogService;
    private final MotivationChildService childService;
    private final MotivationPointExchangeRuleService pointExchangeRuleService;

    private static final Set<String> POINT_TYPES = Set.of(
            MotivationEnums.PointType.STAR.code(),
            MotivationEnums.PointType.FLOWER.code(),
            MotivationEnums.PointType.CROWN.code());

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
        PaymentPlan paymentPlan = buildPaymentPlan(reward, request.getPaymentPointType(), userId);
        MotivationRewardExchange exchange = new MotivationRewardExchange();
        exchange.setRewardId(reward.getId());
        exchange.setChildId(reward.getChildId());
        exchange.setRewardNameSnapshot(reward.getName());
        exchange.setRewardColorSnapshot(reward.getRewardColor());
        exchange.setRewardIconSnapshot(reward.getRewardIcon());
        exchange.setRequiredPointType(paymentPlan.requiredPointType());
        exchange.setRequiredPointsSnapshot(reward.getRequiredPoints());
        exchange.setFulfillmentStatus(MotivationEnums.RewardFulfillmentStatus.PENDING.code());
        exchange.setRemark(request.getRemark());
        exchange.setRequestedByUserId(userId);
        exchange.setRequestedAt(LocalDateTime.now());

        exchange.setStatus(MotivationEnums.RewardExchangeStatus.REQUESTED.code());
        save(exchange);
        MotivationPointLedger ledger = deductRewardPoints(exchange, paymentPlan, userId);
        exchange.setDeductedLedgerId(ledger.getId());
        updateById(exchange);
        systemLogService.recordBusiness(userId, reward.getChildId(), MotivationEnums.LogType.REWARD,
                "申请兑换奖励", "申请兑换了奖励「" + reward.getName() + "」");
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange approve(Long exchangeId, RewardExchangeReviewRequest request, Long userId) {
        MotivationRewardExchange exchange = requireExchange(exchangeId, userId);
        if (MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.APPROVED, exchange.getStatus())
                || MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.COMPLETED, exchange.getStatus())) {
            return exchange;
        }
        if (!MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.REQUESTED, exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_REQUESTED", "只有待确认兑换可以通过");
        }
        MotivationReward reward = rewardService.requireActiveReward(exchange.getRewardId(), userId);
        validateStock(reward);
        MotivationPointLedger ledger = ensureRewardPointsDeducted(exchange, reward, userId);
        exchange.setStatus(MotivationEnums.RewardExchangeStatus.APPROVED.code());
        exchange.setFulfillmentStatus(MotivationEnums.RewardFulfillmentStatus.PENDING.code());
        exchange.setReviewedByUserId(userId);
        exchange.setReviewedAt(LocalDateTime.now());
        exchange.setDeductedLedgerId(ledger.getId());
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        decreaseStock(reward);
        systemLogService.recordBusiness(userId, exchange.getChildId(), MotivationEnums.LogType.REWARD,
                "确认奖励兑换", "通过了奖励「" + exchange.getRewardNameSnapshot() + "」的兑换申请");
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange reject(Long exchangeId, RewardExchangeReviewRequest request, Long userId) {
        MotivationRewardExchange exchange = requireExchange(exchangeId, userId);
        if (!MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.REQUESTED, exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_REQUESTED", "只有待确认兑换可以拒绝");
        }
        refundRewardPoints(exchange, userId);
        exchange.setStatus(MotivationEnums.RewardExchangeStatus.REJECTED.code());
        exchange.setReviewedByUserId(userId);
        exchange.setReviewedAt(LocalDateTime.now());
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.recordBusiness(userId, exchange.getChildId(), MotivationEnums.LogType.REWARD,
                "拒绝奖励兑换", "拒绝了奖励「" + exchange.getRewardNameSnapshot() + "」的兑换申请");
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange updateFulfillment(Long exchangeId, RewardFulfillmentRequest request, Long userId) {
        MotivationRewardExchange exchange = requireManageExchange(exchangeId, userId);
        if (!MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.APPROVED, exchange.getStatus())
                && !MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.COMPLETED, exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_APPROVED", "只有已通过兑换可以更新礼物状态");
        }
        String fulfillmentStatus = normalizeFulfillmentStatus(request == null ? null : request.getFulfillmentStatus(), false);
        exchange.setFulfillmentStatus(fulfillmentStatus);
        exchange.setFulfillmentUpdatedByUserId(userId);
        exchange.setFulfillmentUpdatedAt(LocalDateTime.now());
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentStatus.COMPLETED, fulfillmentStatus)) {
            exchange.setCompletedAt(LocalDateTime.now());
        }
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.recordBusiness(userId, exchange.getChildId(), MotivationEnums.LogType.REWARD,
                "更新礼物状态", "把奖励「" + exchange.getRewardNameSnapshot() + "」的状态改成了 "
                        + MotivationEnums.descriptionOf(MotivationEnums.RewardFulfillmentStatus.class,
                        fulfillmentStatus,
                        MotivationEnums.RewardFulfillmentStatus.PENDING));
        return exchange;
    }

    @Transactional
    public MotivationRewardExchange confirm(Long exchangeId, RewardExchangeConfirmRequest request, Long userId) {
        MotivationRewardExchange exchange = requireViewExchange(exchangeId, userId);
        if (!MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.APPROVED, exchange.getStatus())
                && !MotivationEnums.codeEquals(MotivationEnums.RewardExchangeStatus.COMPLETED, exchange.getStatus())) {
            throw new MotivationException("REWARD_EXCHANGE_NOT_APPROVED", "只有已通过兑换可以确认礼物");
        }
        exchange.setStatus(MotivationEnums.RewardExchangeStatus.COMPLETED.code());
        exchange.setFulfillmentStatus(MotivationEnums.RewardFulfillmentStatus.CONFIRMED.code());
        exchange.setConfirmedByUserId(userId);
        exchange.setConfirmedAt(LocalDateTime.now());
        exchange.setCompletedAt(LocalDateTime.now());
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.recordBusiness(userId, exchange.getChildId(), MotivationEnums.LogType.REWARD,
                "确认礼物兑换券", "确认收到了奖励「" + exchange.getRewardNameSnapshot() + "」");
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
        if (allowConfirmed && MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentStatus.CONFIRMED, normalized)) {
            return normalized;
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentStatus.SCHEDULED, normalized)
                || MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentStatus.IN_PROGRESS, normalized)
                || MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentStatus.COMPLETED, normalized)) {
            return normalized;
        }
        return MotivationEnums.RewardFulfillmentStatus.PENDING.code();
    }

    private MotivationPointLedger ensureRewardPointsDeducted(MotivationRewardExchange exchange,
                                                             MotivationReward reward,
                                                             Long userId) {
        if (exchange.getDeductedLedgerId() != null) {
            MotivationPointLedger ledger = pointLedgerService.getById(exchange.getDeductedLedgerId());
            if (ledger != null) {
                return ledger;
            }
        }
        PaymentPlan paymentPlan = buildPaymentPlan(reward, exchange.getRequiredPointType(), userId);
        return deductRewardPoints(exchange, paymentPlan, userId);
    }

    private MotivationPointLedger deductRewardPoints(MotivationRewardExchange exchange, PaymentPlan paymentPlan, Long userId) {
        int requiredPoints = exchange.getRequiredPointsSnapshot() == null ? 0 : exchange.getRequiredPointsSnapshot();
        if (requiredPoints <= 0) {
            throw new MotivationException("REWARD_POINTS_REQUIRED", "奖励所需积分必须大于 0");
        }
        MotivationPointLedger ledger = pointLedgerService.applyChange(exchange.getChildId(),
                paymentPlan.paymentPointType(),
                -paymentPlan.paymentAmount(),
                MotivationConstants.LedgerSourceType.REWARD_EXCHANGE,
                exchange.getId(),
                exchange.getRewardNameSnapshot(),
                paymentPlan.reason(),
                userId);
        if (paymentPlan.changeAmount() > 0) {
            pointLedgerService.applyChange(exchange.getChildId(),
                    paymentPlan.requiredPointType(),
                    paymentPlan.changeAmount(),
                    MotivationConstants.LedgerSourceType.REWARD_EXCHANGE_CHANGE,
                    exchange.getId(),
                    exchange.getRewardNameSnapshot(),
                    "高币值支付找零",
                    userId);
        }
        return ledger;
    }

    private void refundRewardPoints(MotivationRewardExchange exchange, Long userId) {
        if (exchange.getDeductedLedgerId() == null) {
            return;
        }
        MotivationPointLedger deductedLedger = pointLedgerService.getById(exchange.getDeductedLedgerId());
        if (deductedLedger == null || deductedLedger.getChangeAmount() == null || deductedLedger.getChangeAmount() >= 0) {
            return;
        }
        int refundAmount = Math.abs(deductedLedger.getChangeAmount());
        pointLedgerService.applyChange(exchange.getChildId(),
                deductedLedger.getPointType(),
                refundAmount,
                MotivationConstants.LedgerSourceType.REWARD_EXCHANGE_REFUND,
                exchange.getId(),
                exchange.getRewardNameSnapshot(),
                "兑换拒绝返还",
                userId);
        if (!deductedLedger.getPointType().equals(exchange.getRequiredPointType())) {
            MotivationPointLedger changeLedger = pointLedgerService.lastBySource(
                    MotivationConstants.LedgerSourceType.REWARD_EXCHANGE_CHANGE,
                    exchange.getId(),
                    exchange.getChildId(),
                    exchange.getRequiredPointType());
            if (changeLedger != null && changeLedger.getChangeAmount() != null && changeLedger.getChangeAmount() > 0) {
                pointLedgerService.applyChange(exchange.getChildId(),
                        exchange.getRequiredPointType(),
                        -changeLedger.getChangeAmount(),
                        MotivationConstants.LedgerSourceType.REWARD_EXCHANGE_REFUND,
                        refundSourceId(exchange.getId()),
                        exchange.getRewardNameSnapshot() + " 找零回收",
                        "兑换拒绝回收找零",
                        userId);
            }
        }
    }

    private PaymentPlan buildPaymentPlan(MotivationReward reward, String requestedPaymentPointType, Long userId) {
        String requiredPointType = normalizePointType(reward.getRequiredPointType());
        int requiredPoints = reward.getRequiredPoints() == null ? 0 : reward.getRequiredPoints();
        if (requiredPoints <= 0) {
            throw new MotivationException("REWARD_POINTS_REQUIRED", "奖励所需积分必须大于 0");
        }
        String paymentPointType = StringUtils.hasText(requestedPaymentPointType)
                ? normalizePointType(requestedPaymentPointType)
                : requiredPointType;
        PointExchangeRuleResponse rule = pointExchangeRuleService.getRule(reward.getChildId(), userId);
        int requiredWeight = pointWeight(requiredPointType, rule);
        int paymentWeight = pointWeight(paymentPointType, rule);
        if (paymentWeight < requiredWeight) {
            throw new MotivationException("REWARD_PAYMENT_TYPE_INVALID", "只能使用同币值或更高币值兑换奖励");
        }
        int totalValue = requiredPoints * requiredWeight;
        int paymentAmount = (int) Math.ceil((double) totalValue / paymentWeight);
        int changeValue = paymentAmount * paymentWeight - totalValue;
        int changeAmount = paymentPointType.equals(requiredPointType) ? 0 : (int) Math.ceil((double) changeValue / requiredWeight);
        String reason = paymentPointType.equals(requiredPointType)
                ? "奖励兑换扣减"
                : "奖励兑换扣减，高币值支付";
        return new PaymentPlan(requiredPointType, paymentPointType, paymentAmount, changeAmount, reason);
    }

    private String normalizePointType(String pointType) {
        String normalized = StringUtils.hasText(pointType) ? pointType.trim().toUpperCase() : MotivationEnums.PointType.STAR.code();
        if (!POINT_TYPES.contains(normalized)) {
            throw new MotivationException("POINT_TYPE_INVALID", "积分类型不正确");
        }
        return normalized;
    }

    private int pointWeight(String pointType, PointExchangeRuleResponse rule) {
        Map<String, Integer> weights = Map.of(
                MotivationEnums.PointType.STAR.code(), Math.max(1, rule.getStarWeight()),
                MotivationEnums.PointType.FLOWER.code(), Math.max(1, rule.getFlowerWeight()),
                MotivationEnums.PointType.CROWN.code(), Math.max(1, rule.getCrownWeight()));
        return weights.getOrDefault(pointType, 1);
    }

    private Long refundSourceId(Long exchangeId) {
        return exchangeId == null ? null : exchangeId + REFUND_SOURCE_OFFSET;
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

    private record PaymentPlan(String requiredPointType,
                               String paymentPointType,
                               int paymentAmount,
                               int changeAmount,
                               String reason) {
    }
}
