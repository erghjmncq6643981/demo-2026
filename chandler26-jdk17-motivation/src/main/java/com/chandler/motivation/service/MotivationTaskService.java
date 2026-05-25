package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationTask;
import com.chandler.motivation.domain.dto.task.TaskSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationTaskMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MotivationTaskService extends ServiceImpl<MotivationTaskMapper, MotivationTask> {

    private final MotivationChildService childService;
    private final MotivationGoalService goalService;
    private final MotivationSystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    /**
     * 创建任务，并保存周期、时间段、积分类型等规则快照。
     */
    public MotivationTask create(TaskSaveRequest request, Long userId) {
        if (request == null || request.getChildId() == null || request.getGoalId() == null) {
            throw new MotivationException("TASK_SCOPE_REQUIRED", "请选择孩子和目标");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new MotivationException("TASK_NAME_REQUIRED", "任务名称不能为空");
        }
        childService.requireManageAccess(request.getChildId(), userId);
        goalService.requireActiveGoal(request.getGoalId(), request.getChildId());
        String periodType = normalizePeriodType(request.getPeriodType(), request.getScheduleJson());
        MotivationTask task = new MotivationTask();
        task.setChildId(request.getChildId());
        task.setGoalId(request.getGoalId());
        task.setName(request.getName().trim());
        task.setDescription(request.getDescription());
        task.setPeriodType(periodType);
        task.setScheduleJson(normalizeScheduleJson(request.getScheduleJson(), periodType));
        task.setTaskColor(request.getTaskColor());
        task.setPointType(normalizePointType(request.getPointType()).code());
        task.setPointColor(request.getPointColor());
        task.setBasePoints(request.getBasePoints() == null ? 0 : request.getBasePoints());
        task.setRequireApproval(Boolean.TRUE.equals(request.getRequireApproval())
                ? MotivationConstants.Flag.YES
                : MotivationConstants.Flag.NO);
        task.setAllowPenalty(Boolean.FALSE.equals(request.getAllowPenalty())
                ? MotivationConstants.Flag.NO
                : MotivationConstants.Flag.YES);
        task.setStatus(MotivationEnums.TaskStatus.ACTIVE.code());
        task.setDeleted(MotivationConstants.Flag.NO);
        task.setSortNo(request.getSortNo() == null ? MotivationConstants.Sort.DEFAULT_SORT_NO : request.getSortNo());
        task.setCreatedByUserId(userId);
        task.setUpdatedByUserId(userId);
        save(task);
        systemLogService.recordBusiness(userId, task.getChildId(), MotivationEnums.LogType.TASK,
                "创建任务", "创建了任务「" + task.getName() + "」，奖励为 "
                        + task.getBasePoints() + " 个" + pointName(task.getPointType()));
        return task;
    }

    /**
     * 修改任务规则和奖励设置。
     */
    public MotivationTask update(Long taskId, TaskSaveRequest request, Long userId) {
        MotivationTask task = getById(taskId);
        if (task == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(task.getDeleted())) {
            throw new MotivationException("TASK_NOT_FOUND", "任务不存在");
        }
        childService.requireManageAccess(task.getChildId(), userId);
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new MotivationException("TASK_NAME_REQUIRED", "任务名称不能为空");
        }
        Long goalId = request.getGoalId() == null ? task.getGoalId() : request.getGoalId();
        goalService.requireActiveGoal(goalId, task.getChildId());
        String periodType = normalizePeriodType(request.getPeriodType(), request.getScheduleJson());
        task.setGoalId(goalId);
        task.setName(request.getName().trim());
        task.setDescription(request.getDescription());
        task.setPeriodType(periodType);
        task.setScheduleJson(normalizeScheduleJson(request.getScheduleJson(), periodType));
        task.setTaskColor(request.getTaskColor());
        task.setPointType(normalizePointType(request.getPointType()).code());
        task.setPointColor(request.getPointColor());
        task.setBasePoints(request.getBasePoints() == null ? 0 : request.getBasePoints());
        task.setRequireApproval(Boolean.TRUE.equals(request.getRequireApproval())
                ? MotivationConstants.Flag.YES
                : MotivationConstants.Flag.NO);
        task.setAllowPenalty(Boolean.FALSE.equals(request.getAllowPenalty())
                ? MotivationConstants.Flag.NO
                : MotivationConstants.Flag.YES);
        task.setSortNo(request.getSortNo() == null ? task.getSortNo() : request.getSortNo());
        task.setUpdatedByUserId(userId);
        updateById(task);
        systemLogService.recordBusiness(userId, task.getChildId(), MotivationEnums.LogType.TASK,
                "修改任务", "修改了任务「" + task.getName() + "」");
        return task;
    }

    public List<MotivationTask> listByChild(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return list(new LambdaQueryWrapper<MotivationTask>()
                .eq(MotivationTask::getChildId, childId)
                .eq(MotivationTask::getDeleted, MotivationConstants.Flag.NO)
                .orderByAsc(MotivationTask::getSortNo)
                .orderByDesc(MotivationTask::getUpdateTime));
    }

    /**
     * 软删除任务，并将任务状态归档。
     */
    public void delete(Long taskId, Long userId) {
        MotivationTask task = getById(taskId);
        if (task == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(task.getDeleted())) {
            throw new MotivationException("TASK_NOT_FOUND", "任务不存在");
        }
        childService.requireManageAccess(task.getChildId(), userId);
        task.setDeleted(MotivationConstants.Flag.YES);
        task.setStatus(MotivationEnums.TaskStatus.ARCHIVED.code());
        task.setUpdatedByUserId(userId);
        updateById(task);
        systemLogService.recordBusiness(userId, task.getChildId(), MotivationEnums.LogType.TASK,
                "删除任务", "删除了任务「" + task.getName() + "」");
    }

    /**
     * 校验任务存在、可查看且处于启用状态。
     */
    public MotivationTask requireActiveTask(Long taskId, Long userId) {
        MotivationTask task = getById(taskId);
        if (task == null || Integer.valueOf(MotivationConstants.Flag.YES).equals(task.getDeleted())) {
            throw new MotivationException("TASK_NOT_FOUND", "任务不存在");
        }
        childService.requireViewAccess(task.getChildId(), userId);
        if (!MotivationEnums.codeEquals(MotivationEnums.TaskStatus.ACTIVE, task.getStatus())) {
            throw new MotivationException("TASK_NOT_ACTIVE",
                    "任务状态为「" + MotivationEnums.descriptionOf(MotivationEnums.TaskStatus.class,
                            task.getStatus(),
                            MotivationEnums.TaskStatus.ARCHIVED) + "」，不能打卡");
        }
        return task;
    }

    private String normalizePeriodType(String requestPeriodType, String scheduleJson) {
        String fallback = readText(readSchedule(scheduleJson), "type");
        String resolved = StringUtils.hasText(requestPeriodType) ? requestPeriodType.trim() : fallback;
        if (!StringUtils.hasText(resolved)) {
            resolved = MotivationEnums.PeriodType.DAILY.code();
        }
        resolved = resolved.toUpperCase(Locale.ROOT);
        MotivationEnums.PeriodType periodType = MotivationEnums.fromCode(
                MotivationEnums.PeriodType.class,
                resolved,
                null);
        if (periodType == null) {
            throw new MotivationException("TASK_PERIOD_INVALID", "请选择有效的任务周期");
        }
        return periodType.code();
    }

    private String normalizeScheduleJson(String scheduleJson, String periodType) {
        JsonNode source = readSchedule(scheduleJson);
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("type", periodType);
        normalized.put("category", normalizeCategory(readText(source, "category")));

        if (MotivationEnums.codeEquals(MotivationEnums.PeriodType.DAILY, periodType)) {
            int startHour = resolveHour(source, "startHour", MotivationConstants.Schedule.DEFAULT_START_HOUR);
            int endHour = resolveHour(source, "endHour", MotivationConstants.Schedule.DEFAULT_END_HOUR);
            if (startHour > endHour) {
                throw new MotivationException("TASK_TIME_RANGE_INVALID", "开始时间不能晚于结束时间");
            }
            List<Integer> selectedHours = normalizeDays(source.get("hours"),
                    MotivationConstants.Schedule.DEFAULT_START_HOUR,
                    MotivationConstants.Schedule.DEFAULT_END_HOUR);
            if (selectedHours.isEmpty()) {
                selectedHours = new ArrayList<>();
                for (int hour = startHour; hour <= endHour; hour++) {
                    selectedHours.add(hour);
                }
            } else {
                startHour = selectedHours.stream().min(Integer::compareTo).orElse(startHour);
                endHour = selectedHours.stream().max(Integer::compareTo).orElse(endHour);
            }
            ArrayNode hours = normalized.putArray("hours");
            selectedHours.forEach(hours::add);
            ObjectNode timeRange = normalized.putObject("timeRange");
            timeRange.put("startHour", startHour);
            timeRange.put("endHour", endHour);
            normalized.put("requiredCount", MotivationConstants.Schedule.MIN_REQUIRED_COUNT);
            return writeJson(normalized);
        }

        boolean weekly = MotivationEnums.codeEquals(MotivationEnums.PeriodType.WEEKLY, periodType);
        List<Integer> selectedDays = normalizeDays(source.get("days"),
                weekly ? MotivationConstants.Schedule.FIRST_WEEK_DAY : MotivationConstants.Schedule.FIRST_MONTH_DAY,
                weekly ? MotivationConstants.Schedule.LAST_WEEK_DAY : MotivationConstants.Schedule.LAST_MONTH_DAY);
        if (selectedDays.isEmpty()) {
            throw new MotivationException("TASK_DAYS_REQUIRED", weekly
                    ? "请选择每周执行的星期"
                    : "请选择每月执行的日期");
        }
        int requiredCount = resolveRequiredCount(source);
        if (requiredCount < 1 || requiredCount > selectedDays.size()) {
            throw new MotivationException("TASK_REQUIRED_COUNT_INVALID", "完成次数不能大于已选择的执行日期数量");
        }
        ArrayNode days = normalized.putArray("days");
        selectedDays.forEach(days::add);
        normalized.put("requiredCount", requiredCount);
        return writeJson(normalized);
    }

    private JsonNode readSchedule(String scheduleJson) {
        try {
            return objectMapper.readTree(StringUtils.hasText(scheduleJson) ? scheduleJson : "{}");
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new MotivationException("TASK_SCHEDULE_INVALID", "任务规则保存失败");
        }
    }

    private String readText(JsonNode source, String fieldName) {
        if (source == null || source.isNull()) {
            return "";
        }
        JsonNode field = source.get(fieldName);
        return field == null || field.isNull() ? "" : field.asText("");
    }

    private String normalizeCategory(String category) {
        return MotivationEnums.fromCode(
                MotivationEnums.TaskCategory.class,
                StringUtils.hasText(category) ? category.trim().toUpperCase(Locale.ROOT) : null,
                MotivationEnums.TaskCategory.HABIT).code();
    }

    private int resolveHour(JsonNode source, String fieldName, int defaultValue) {
        JsonNode timeRange = source == null ? null : source.get("timeRange");
        JsonNode field = timeRange == null ? null : timeRange.get(fieldName);
        if (field == null || field.isNull()) {
            field = source == null ? null : source.get(fieldName);
        }
        int value = field == null || field.isNull() ? defaultValue : field.asInt(defaultValue);
        if (value < MotivationConstants.Schedule.DEFAULT_START_HOUR
                || value > MotivationConstants.Schedule.DEFAULT_END_HOUR) {
            throw new MotivationException("TASK_TIME_RANGE_INVALID", "时间范围必须在 06:00 到 22:00 之间");
        }
        return value;
    }

    private int resolveRequiredCount(JsonNode source) {
        JsonNode requiredCountNode = source == null ? null : source.get("requiredCount");
        if (requiredCountNode == null || requiredCountNode.isNull()) {
            requiredCountNode = source == null ? null : source.get("timesPerWeek");
        }
        if (requiredCountNode == null || requiredCountNode.isNull()) {
            requiredCountNode = source == null ? null : source.get("timesPerDay");
        }
        int value = requiredCountNode == null || requiredCountNode.isNull()
                ? MotivationConstants.Schedule.MIN_REQUIRED_COUNT
                : requiredCountNode.asInt(MotivationConstants.Schedule.MIN_REQUIRED_COUNT);
        return Math.max(MotivationConstants.Schedule.MIN_REQUIRED_COUNT, value);
    }

    private List<Integer> normalizeDays(JsonNode daysNode, int min, int max) {
        if (daysNode == null || !daysNode.isArray()) {
            return List.of();
        }
        Set<Integer> days = new LinkedHashSet<>();
        for (JsonNode dayNode : daysNode) {
            int day = dayNode.asInt(0);
            if (day >= min && day <= max) {
                days.add(day);
            }
        }
        return new ArrayList<>(days);
    }

    private MotivationEnums.PointType normalizePointType(String pointType) {
        MotivationEnums.PointType resolved = MotivationEnums.fromCode(
                MotivationEnums.PointType.class,
                pointType,
                MotivationEnums.PointType.STAR);
        if (resolved == null) {
            throw new MotivationException("POINT_TYPE_INVALID", "奖励类型不正确");
        }
        return resolved;
    }

    private String pointName(String pointType) {
        return MotivationEnums.descriptionOf(MotivationEnums.PointType.class, pointType, MotivationEnums.PointType.STAR);
    }
}
