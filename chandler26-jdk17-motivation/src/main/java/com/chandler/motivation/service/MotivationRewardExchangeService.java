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
import java.time.LocalDate;
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
                .orderByDesc(MotivationRewardExchange::getUpdateTime)
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
        exchange.setBranchStatus(MotivationEnums.RewardBranchStatus.PENDING.code());
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
        exchange.setBranchStatus(MotivationEnums.RewardBranchStatus.PENDING.code());
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
        MotivationReward reward = rewardService.getById(exchange.getRewardId());
        String fulfillmentType = reward == null ? null : reward.getFulfillmentType();
        String branchStatus = normalizeBranchStatus(resolveRequestedBranchStatus(request, fulfillmentType), fulfillmentType);
        String fulfillmentStatus = deriveFulfillmentStatus(branchStatus);
        exchange.setBranchStatus(branchStatus);
        exchange.setFulfillmentStatus(fulfillmentStatus);
        exchange.setFulfillmentUpdatedByUserId(userId);
        exchange.setFulfillmentUpdatedAt(LocalDateTime.now());
        applyFulfillmentDates(exchange, request);
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentStatus.COMPLETED, fulfillmentStatus)) {
            exchange.setCompletedAt(LocalDateTime.now());
        }
        if (request != null && request.getRemark() != null && !request.getRemark().isBlank()) {
            exchange.setRemark(request.getRemark().trim());
        }
        updateById(exchange);
        systemLogService.recordBusiness(userId, exchange.getChildId(), MotivationEnums.LogType.REWARD,
                "更新礼物状态", "把奖励「" + exchange.getRewardNameSnapshot() + "」的状态改成了 "
                        + MotivationEnums.descriptionOf(MotivationEnums.RewardBranchStatus.class,
                        branchStatus,
                        MotivationEnums.RewardBranchStatus.PENDING));
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
        exchange.setBranchStatus(MotivationEnums.RewardBranchStatus.COMPLETED.code());
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

    private String normalizeBranchStatus(String branchStatus, String fulfillmentType) {
        String normalized = branchStatus == null ? "" : branchStatus.trim().toUpperCase();
        MotivationEnums.RewardBranchStatus resolved = MotivationEnums.fromCode(
                MotivationEnums.RewardBranchStatus.class,
                normalized,
                MotivationEnums.RewardBranchStatus.PENDING);
        if (resolved == MotivationEnums.RewardBranchStatus.PENDING) {
            return resolved.code();
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentType.PARENT_PURCHASE, fulfillmentType)
                && (resolved == MotivationEnums.RewardBranchStatus.PURCHASE_ORDERED
                || resolved == MotivationEnums.RewardBranchStatus.PURCHASE_SHIPPING
                || resolved == MotivationEnums.RewardBranchStatus.PURCHASE_ARRIVED
                || resolved == MotivationEnums.RewardBranchStatus.COMPLETED)) {
            return resolved.code();
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentType.PARENT_FULFILL, fulfillmentType)
                && (resolved == MotivationEnums.RewardBranchStatus.SCHEDULED
                || resolved == MotivationEnums.RewardBranchStatus.IN_PROGRESS
                || resolved == MotivationEnums.RewardBranchStatus.COMPLETED)) {
            return resolved.code();
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentType.PARENT_EXECUTE, fulfillmentType)
                && (resolved == MotivationEnums.RewardBranchStatus.IN_PROGRESS
                || resolved == MotivationEnums.RewardBranchStatus.COMPLETED)) {
            return resolved.code();
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentType.INVENTORY_DEDUCT, fulfillmentType)
                && resolved == MotivationEnums.RewardBranchStatus.COMPLETED) {
            return resolved.code();
        }
        throw new MotivationException("REWARD_BRANCH_STATUS_INVALID", "当前奖励类型不支持该履约状态");
    }

    private String resolveRequestedBranchStatus(RewardFulfillmentRequest request, String fulfillmentType) {
        if (request == null) {
            return MotivationEnums.RewardBranchStatus.PENDING.code();
        }
        if (StringUtils.hasText(request.getBranchStatus())) {
            return request.getBranchStatus();
        }
        String status = request.getFulfillmentStatus();
        if (!StringUtils.hasText(status)) {
            return MotivationEnums.RewardBranchStatus.PENDING.code();
        }
        MotivationEnums.RewardFulfillmentStatus fulfillmentStatus = MotivationEnums.fromCode(
                MotivationEnums.RewardFulfillmentStatus.class,
                status,
                MotivationEnums.RewardFulfillmentStatus.PENDING);
        if (fulfillmentStatus == MotivationEnums.RewardFulfillmentStatus.COMPLETED
                && MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentType.PARENT_PURCHASE, fulfillmentType)) {
            return MotivationEnums.RewardBranchStatus.PURCHASE_ARRIVED.code();
        }
        if (fulfillmentStatus == MotivationEnums.RewardFulfillmentStatus.IN_PROGRESS
                && MotivationEnums.codeEquals(MotivationEnums.RewardFulfillmentType.PARENT_PURCHASE, fulfillmentType)) {
            return MotivationEnums.RewardBranchStatus.PURCHASE_SHIPPING.code();
        }
        if (fulfillmentStatus == MotivationEnums.RewardFulfillmentStatus.SCHEDULED) {
            return MotivationEnums.RewardBranchStatus.SCHEDULED.code();
        }
        if (fulfillmentStatus == MotivationEnums.RewardFulfillmentStatus.IN_PROGRESS) {
            return MotivationEnums.RewardBranchStatus.IN_PROGRESS.code();
        }
        if (fulfillmentStatus == MotivationEnums.RewardFulfillmentStatus.COMPLETED) {
            return MotivationEnums.RewardBranchStatus.COMPLETED.code();
        }
        return MotivationEnums.RewardBranchStatus.PENDING.code();
    }

    private String deriveFulfillmentStatus(String branchStatus) {
        if (MotivationEnums.codeEquals(MotivationEnums.RewardBranchStatus.SCHEDULED, branchStatus)) {
            return MotivationEnums.RewardFulfillmentStatus.SCHEDULED.code();
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardBranchStatus.COMPLETED, branchStatus)
                || MotivationEnums.codeEquals(MotivationEnums.RewardBranchStatus.PURCHASE_ARRIVED, branchStatus)) {
            return MotivationEnums.RewardFulfillmentStatus.COMPLETED.code();
        }
        if (MotivationEnums.codeEquals(MotivationEnums.RewardBranchStatus.PENDING, branchStatus)) {
            return MotivationEnums.RewardFulfillmentStatus.PENDING.code();
        }
        return MotivationEnums.RewardFulfillmentStatus.IN_PROGRESS.code();
    }

    private void applyFulfillmentDates(MotivationRewardExchange exchange, RewardFulfillmentRequest request) {
        if (request == null) {
            return;
        }
        if (request.getExpectedArrivalDate() != null) {
            exchange.setExpectedArrivalDate(request.getExpectedArrivalDate());
        }
        LocalDate startDate = request.getScheduleStartDate();
        LocalDate endDate = request.getScheduleEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new MotivationException("REWARD_SCHEDULE_RANGE_INVALID", "奖励日程结束时间不能早于开始时间");
        }
        if (startDate != null) {
            exchange.setScheduleStartDate(startDate);
        }
        if (endDate != null) {
            exchange.setScheduleEndDate(endDate);
        }
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
            throw new MotivationException("REWARD_POINTS_REQUIRED", "奖励所需货币数量必须大于 0");
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
            throw new MotivationException("REWARD_POINTS_REQUIRED", "奖励所需货币数量必须大于 0");
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
            throw new MotivationException("POINT_TYPE_INVALID", "货币类型不正确");
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
