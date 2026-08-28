package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向其他业务域暴露学习计划的最小查询边界，避免跨域直接依赖学习域 Mapper。
 */
@Service
@RequiredArgsConstructor
public class LearningPlanAccessService {

    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;

    /** 按用户批量统计有效学习计划数。 */
    public Map<Long, Integer> countByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = planMapper.selectMaps(new QueryWrapper<LearningPlan>()
                .select("user_id AS userId", "COUNT(*) AS count")
                .in("user_id", userIds)
                .eq("deleted", false)
                .groupBy("user_id"));
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = number(row.get("userId"));
            Long count = number(row.get("count"));
            if (userId != null && count != null) {
                result.put(userId, count.intValue());
            }
        }
        return Map.copyOf(result);
    }

    /** 校验并返回用户拥有的学习计划。 */
    public LearningPlan requireOwnedPlan(Long userId, Long planId) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (plan == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_PLAN_NOT_FOUND,
                    "学习计划不存在: " + planId);
        }
        return plan;
    }

    /** 校验并返回学习计划中的单元。 */
    public LearningPlanUnit requireUnit(LearningPlan plan, Long unitId) {
        LearningPlanUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getId, unitId)
                .eq(LearningPlanUnit::getPlanId, plan.getId())
                .eq(LearningPlanUnit::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (unit == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND,
                    "场景学习单元不存在: " + unitId);
        }
        return unit;
    }

    /** 在当前事务中锁定学习单元，防止并发创建重复任务。 */
    public LearningPlanUnit lockUnit(Long planId, Long unitId) {
        LearningPlanUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getId, unitId)
                .eq(LearningPlanUnit::getPlanId, planId)
                .eq(LearningPlanUnit::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE + " FOR UPDATE"));
        if (unit == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND,
                    "场景学习单元不存在: " + unitId);
        }
        return unit;
    }

    /** 查询一个单元中需要生成词卡的核心词和复习词。 */
    public List<LearningPlanUnitEntry> listVocabularyCardEntries(Long unitId) {
        return unitEntryMapper.selectList(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                .eq(LearningPlanUnitEntry::getUnitId, unitId)
                .in(LearningPlanUnitEntry::getTier,
                        List.of(ScenePlanConstants.TIER_CORE, ScenePlanConstants.TIER_REVIEW))
                .isNotNull(LearningPlanUnitEntry::getWordbookEntryId)
                .eq(LearningPlanUnitEntry::getDeleted, false)
                .orderByAsc(LearningPlanUnitEntry::getSortOrder));
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
