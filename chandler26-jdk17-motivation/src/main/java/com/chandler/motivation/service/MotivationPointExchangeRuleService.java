package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationPointExchangeRule;
import com.chandler.motivation.domain.dataobject.MotivationPointLedger;
import com.chandler.motivation.domain.dto.points.PointExchangeRequest;
import com.chandler.motivation.domain.dto.points.PointExchangeResultResponse;
import com.chandler.motivation.domain.dto.points.PointExchangeRuleRequest;
import com.chandler.motivation.domain.dto.points.PointExchangeRuleResponse;
import com.chandler.motivation.domain.mapper.MotivationPointExchangeRuleMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationPointExchangeRuleService extends ServiceImpl<MotivationPointExchangeRuleMapper, MotivationPointExchangeRule> {

    private static final int DEFAULT_STAR_WEIGHT = 1;
    private static final int DEFAULT_FLOWER_WEIGHT = 10;
    private static final int DEFAULT_CROWN_WEIGHT = 100;
    private static final Set<String> POINT_TYPES = Set.of(
            MotivationEnums.PointType.STAR.code(),
            MotivationEnums.PointType.FLOWER.code(),
            MotivationEnums.PointType.CROWN.code());

    private final MotivationChildService childService;
    private final MotivationPointLedgerService pointLedgerService;
    private final MotivationSystemLogService systemLogService;

    /**
     * 获取孩子币值兑换规则。
     */
    public PointExchangeRuleResponse getRule(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return toResponse(getOrDefault(childId));
    }

    /**
     * 保存孩子币值之间的兑换比例。
     */
    @Transactional
    public PointExchangeRuleResponse saveRule(Long childId, Long userId, PointExchangeRuleRequest request) {
        childService.requireManageAccess(childId, userId);
        MotivationPointExchangeRule rule = getOrDefault(childId);
        int starWeight = positiveWeight(request == null ? null : request.getStarWeight(), DEFAULT_STAR_WEIGHT);
        int flowerWeight = positiveWeight(request == null ? null : request.getFlowerWeight(), DEFAULT_FLOWER_WEIGHT);
        int crownWeight = positiveWeight(request == null ? null : request.getCrownWeight(), DEFAULT_CROWN_WEIGHT);
        if (!(starWeight < flowerWeight && flowerWeight < crownWeight)) {
            throw new MotivationException("POINT_EXCHANGE_RATIO_INVALID", "币值必须满足：星星币 < 红花币 < 皇冠币");
        }
        rule.setStarWeight(starWeight);
        rule.setFlowerWeight(flowerWeight);
        rule.setCrownWeight(crownWeight);
        rule.setUpdatedByUserId(userId);
        if (rule.getId() == null) {
            rule.setChildId(childId);
            rule.setCreatedByUserId(userId);
            save(rule);
        } else {
            updateById(rule);
        }
        systemLogService.recordBusiness(userId, childId, MotivationEnums.LogType.POINT,
                "设置币值",
                "设置了币值兑换比例：星星币 1:" + starWeight + "，红花币 1:" + flowerWeight + "，皇冠币 1:" + crownWeight);
        return toResponse(rule);
    }

    /**
     * 在孩子侧执行币值互换。
     */
    @Transactional
    public PointExchangeResultResponse exchange(Long childId, Long userId, PointExchangeRequest request) {
        childService.requireViewAccess(childId, userId);
        if (request == null) {
            throw new MotivationException("POINT_EXCHANGE_REQUIRED", "请填写积分兑换信息");
        }
        String fromPointType = normalizePointType(request.getFromPointType());
        String toPointType = normalizePointType(request.getToPointType());
        if (fromPointType.equals(toPointType)) {
            throw new MotivationException("POINT_EXCHANGE_SAME_TYPE", "请选择不同的积分类型");
        }
        int fromAmount = request.getFromAmount() == null ? 0 : request.getFromAmount();
        if (fromAmount <= 0) {
            throw new MotivationException("POINT_EXCHANGE_AMOUNT_REQUIRED", "兑换数量必须大于 0");
        }
        MotivationPointExchangeRule rule = getOrDefault(childId);
        int fromWeight = weightOf(rule, fromPointType);
        int toWeight = weightOf(rule, toPointType);
        int toAmount = (fromAmount * fromWeight) / toWeight;
        if (toAmount <= 0) {
            throw new MotivationException("POINT_EXCHANGE_AMOUNT_TOO_SMALL", "当前数量不足以兑换目标积分");
        }
        int spentAmount = (int) Math.ceil((double) toAmount * toWeight / fromWeight);
        long sourceId = System.currentTimeMillis();
        String sourceName = pointName(fromPointType) + "兑换" + pointName(toPointType);
        MotivationPointLedger spentLedger = pointLedgerService.applyChange(childId,
                fromPointType,
                -spentAmount,
                MotivationConstants.LedgerSourceType.POINT_EXCHANGE,
                sourceId,
                sourceName,
                "积分互换扣减",
                userId);
        MotivationPointLedger earnedLedger = pointLedgerService.applyChange(childId,
                toPointType,
                toAmount,
                MotivationConstants.LedgerSourceType.POINT_EXCHANGE,
                sourceId + 1,
                sourceName,
                "积分互换入账",
                userId);
        systemLogService.recordBusiness(userId, childId, MotivationEnums.LogType.POINT,
                "积分互换",
                "使用 " + spentAmount + " 个" + pointName(fromPointType) + " 兑换了 " + toAmount + " 个" + pointName(toPointType));

        PointExchangeResultResponse response = new PointExchangeResultResponse();
        response.setFromPointType(fromPointType);
        response.setToPointType(toPointType);
        response.setFromAmount(spentAmount);
        response.setToAmount(toAmount);
        response.setExchangeRule(toResponse(rule));
        response.setSpentLedger(spentLedger);
        response.setEarnedLedger(earnedLedger);
        return response;
    }

    private MotivationPointExchangeRule getOrDefault(Long childId) {
        MotivationPointExchangeRule rule = getOne(new LambdaQueryWrapper<MotivationPointExchangeRule>()
                .eq(MotivationPointExchangeRule::getChildId, childId)
                .last("limit 1"));
        if (rule != null) {
            return rule;
        }
        MotivationPointExchangeRule defaultRule = new MotivationPointExchangeRule();
        defaultRule.setChildId(childId);
        defaultRule.setStarWeight(DEFAULT_STAR_WEIGHT);
        defaultRule.setFlowerWeight(DEFAULT_FLOWER_WEIGHT);
        defaultRule.setCrownWeight(DEFAULT_CROWN_WEIGHT);
        return defaultRule;
    }

    private int positiveWeight(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private String normalizePointType(String pointType) {
        String normalized = StringUtils.hasText(pointType) ? pointType.trim().toUpperCase() : "";
        if (!POINT_TYPES.contains(normalized)) {
            throw new MotivationException("POINT_TYPE_INVALID", "积分类型不正确");
        }
        return normalized;
    }

    private int weightOf(MotivationPointExchangeRule rule, String pointType) {
        return switch (pointType) {
            case "CROWN" -> rule.getCrownWeight();
            case "FLOWER" -> rule.getFlowerWeight();
            default -> rule.getStarWeight();
        };
    }

    private String pointName(String pointType) {
        return switch (pointType) {
            case "CROWN" -> "皇冠";
            case "FLOWER" -> "红花";
            default -> "星星";
        };
    }

    private PointExchangeRuleResponse toResponse(MotivationPointExchangeRule rule) {
        PointExchangeRuleResponse response = new PointExchangeRuleResponse();
        response.setChildId(rule.getChildId());
        response.setStarWeight(rule.getStarWeight() == null ? DEFAULT_STAR_WEIGHT : rule.getStarWeight());
        response.setFlowerWeight(rule.getFlowerWeight() == null ? DEFAULT_FLOWER_WEIGHT : rule.getFlowerWeight());
        response.setCrownWeight(rule.getCrownWeight() == null ? DEFAULT_CROWN_WEIGHT : rule.getCrownWeight());
        return response;
    }
}
