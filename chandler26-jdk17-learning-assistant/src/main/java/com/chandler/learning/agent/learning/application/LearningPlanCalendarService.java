package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.api.response.LearningPlanCalendarDayResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 词汇大挑战日历的按需汇总查询，避免加载单元完整材料。 */
@Service
@RequiredArgsConstructor
public class LearningPlanCalendarService {

    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanResponseAssembler responseAssembler;
    private final AiAsyncTaskService aiAsyncTaskService;

    /** 查询一段日期内的日历摘要；一次最多 63 天。 */
    public List<LearningPlanCalendarDayResponse> calendar(Long userId, Long planId,
                                                          LocalDate from, LocalDate to) {
        LearningPlan plan = requirePlan(userId, planId);
        LocalDate today = LocalDate.now();
        LocalDate resolvedFrom = from == null ? today.with(java.time.DayOfWeek.MONDAY) : from;
        LocalDate resolvedTo = to == null ? resolvedFrom.plusDays(6) : to;
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "日历结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > 62) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "单次日历查询不能超过 63 天");
        }
        List<LearningPlanUnit> units = unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, plan.getId())
                .ge(LearningPlanUnit::getRecommendedDate, resolvedFrom)
                .le(LearningPlanUnit::getRecommendedDate, resolvedTo)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getRecommendedDate)
                .orderByAsc(LearningPlanUnit::getUnitNo));
        Map<LocalDate, List<LearningPlanUnit>> unitsByDate = units.stream()
                .collect(Collectors.groupingBy(LearningPlanUnit::getRecommendedDate,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, LearningPlanUnitResponse> summariesByUnit = responseAssembler
                .toUnitSummaryResponses(units, plan.getId())
                .stream()
                .collect(Collectors.toMap(LearningPlanUnitResponse::getId, response -> response));
        Set<LocalDate> generatingDates = aiAsyncTaskService.findActiveGeneratingDatesForPlan(userId, plan.getId());
        List<LearningPlanCalendarDayResponse> result = new ArrayList<>();
        for (LocalDate date = resolvedFrom; !date.isAfter(resolvedTo); date = date.plusDays(1)) {
            List<LearningPlanUnit> dateUnits = unitsByDate.getOrDefault(date, List.of());
            int planned = dateUnits.stream().mapToInt(unit -> value(unit.getCoreWordCount())).sum();
            int pending = dateUnits.stream().mapToInt(unit -> Math.max(CommonConstants.ZERO,
                    value(unit.getCoreWordCount()) - value(unit.getCompletedCoreCount()))).sum();
            LearningPlanCalendarDayResponse day = new LearningPlanCalendarDayResponse();
            day.setDate(date);
            day.setPlannedWordCount(planned);
            day.setPendingChallengeCount(pending);
            day.setGeneratedUnitCount(dateUnits.size());
            day.setCompletedUnitCount((int) dateUnits.stream()
                    .filter(unit -> ScenePlanConstants.UNIT_COMPLETED.equals(unit.getStatus())).count());
            day.setOverdueCount(date.isBefore(today) ? pending : CommonConstants.ZERO);
            day.setGenerating(generatingDates.contains(date));
            day.setUnits(dateUnits.stream().map(LearningPlanUnit::getId).map(summariesByUnit::get)
                    .filter(Objects::nonNull).toList());
            result.add(day);
        }
        return result;
    }

    private LearningPlan requirePlan(Long userId, Long planId) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (plan == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.LEARNING_PLAN_NOT_FOUND,
                    "学习计划不存在: " + planId);
        }
        return plan;
    }

    private int value(Integer value) {
        return value == null ? CommonConstants.ZERO : value;
    }
}
