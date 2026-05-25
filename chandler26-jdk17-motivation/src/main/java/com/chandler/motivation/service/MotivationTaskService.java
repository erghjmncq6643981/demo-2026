package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationTask;
import com.chandler.motivation.domain.dto.task.TaskSaveRequest;
import com.chandler.motivation.domain.mapper.MotivationTaskMapper;
import com.chandler.motivation.support.MotivationConstants;
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
        task.setPointType(StringUtils.hasText(request.getPointType()) ? request.getPointType() : MotivationConstants.PointType.STAR);
        task.setPointColor(request.getPointColor());
        task.setBasePoints(request.getBasePoints() == null ? 0 : request.getBasePoints());
        task.setRequireApproval(Boolean.TRUE.equals(request.getRequireApproval()) ? 1 : 0);
        task.setAllowPenalty(Boolean.FALSE.equals(request.getAllowPenalty()) ? 0 : 1);
        task.setStatus(MotivationConstants.TaskStatus.ACTIVE);
        task.setDeleted(0);
        task.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        task.setCreatedByUserId(userId);
        task.setUpdatedByUserId(userId);
        save(task);
        systemLogService.record(userId, task.getChildId(), MotivationConstants.LogType.TASK,
                "创建任务", "创建任务「" + task.getName() + "」，基础积分 " + task.getBasePoints());
        return task;
    }

    public MotivationTask update(Long taskId, TaskSaveRequest request, Long userId) {
        MotivationTask task = getById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
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
        task.setPointType(StringUtils.hasText(request.getPointType()) ? request.getPointType() : MotivationConstants.PointType.STAR);
        task.setPointColor(request.getPointColor());
        task.setBasePoints(request.getBasePoints() == null ? 0 : request.getBasePoints());
        task.setRequireApproval(Boolean.TRUE.equals(request.getRequireApproval()) ? 1 : 0);
        task.setAllowPenalty(Boolean.FALSE.equals(request.getAllowPenalty()) ? 0 : 1);
        task.setSortNo(request.getSortNo() == null ? task.getSortNo() : request.getSortNo());
        task.setUpdatedByUserId(userId);
        updateById(task);
        systemLogService.record(userId, task.getChildId(), MotivationConstants.LogType.TASK,
                "修改任务", "修改任务「" + task.getName() + "」");
        return task;
    }

    public List<MotivationTask> listByChild(Long childId, Long userId) {
        childService.requireViewAccess(childId, userId);
        return list(new LambdaQueryWrapper<MotivationTask>()
                .eq(MotivationTask::getChildId, childId)
                .eq(MotivationTask::getDeleted, 0)
                .orderByAsc(MotivationTask::getSortNo)
                .orderByDesc(MotivationTask::getUpdateTime));
    }

    public void delete(Long taskId, Long userId) {
        MotivationTask task = getById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new MotivationException("TASK_NOT_FOUND", "任务不存在");
        }
        childService.requireManageAccess(task.getChildId(), userId);
        task.setDeleted(1);
        task.setStatus(MotivationConstants.TaskStatus.ARCHIVED);
        task.setUpdatedByUserId(userId);
        updateById(task);
        systemLogService.record(userId, task.getChildId(), MotivationConstants.LogType.TASK,
                "删除任务", "删除任务「" + task.getName() + "」");
    }

    public MotivationTask requireActiveTask(Long taskId, Long userId) {
        MotivationTask task = getById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new MotivationException("TASK_NOT_FOUND", "任务不存在");
        }
        childService.requireViewAccess(task.getChildId(), userId);
        if (!MotivationConstants.TaskStatus.ACTIVE.equals(task.getStatus())) {
            throw new MotivationException("TASK_NOT_ACTIVE", "任务未启用");
        }
        return task;
    }

    private String normalizePeriodType(String requestPeriodType, String scheduleJson) {
        String fallback = readText(readSchedule(scheduleJson), "type");
        String resolved = StringUtils.hasText(requestPeriodType) ? requestPeriodType.trim() : fallback;
        if (!StringUtils.hasText(resolved)) {
            resolved = MotivationConstants.PeriodType.DAILY;
        }
        resolved = resolved.toUpperCase(Locale.ROOT);
        if (!MotivationConstants.PeriodType.DAILY.equals(resolved)
                && !MotivationConstants.PeriodType.WEEKLY.equals(resolved)
                && !MotivationConstants.PeriodType.MONTHLY.equals(resolved)) {
            throw new MotivationException("TASK_PERIOD_INVALID", "请选择有效的任务周期");
        }
        return resolved;
    }

    private String normalizeScheduleJson(String scheduleJson, String periodType) {
        JsonNode source = readSchedule(scheduleJson);
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("type", periodType);
        normalized.put("category", normalizeCategory(readText(source, "category")));

        if (MotivationConstants.PeriodType.DAILY.equals(periodType)) {
            int startHour = resolveHour(source, "startHour", 6);
            int endHour = resolveHour(source, "endHour", 22);
            if (startHour > endHour) {
                throw new MotivationException("TASK_TIME_RANGE_INVALID", "开始时间不能晚于结束时间");
            }
            List<Integer> selectedHours = normalizeDays(source.get("hours"), 6, 22);
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
            normalized.put("requiredCount", 1);
            return writeJson(normalized);
        }

        List<Integer> selectedDays = normalizeDays(source.get("days"), MotivationConstants.PeriodType.WEEKLY.equals(periodType) ? 1 : 1,
                MotivationConstants.PeriodType.WEEKLY.equals(periodType) ? 7 : 31);
        if (selectedDays.isEmpty()) {
            throw new MotivationException("TASK_DAYS_REQUIRED", MotivationConstants.PeriodType.WEEKLY.equals(periodType)
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
        String resolved = StringUtils.hasText(category) ? category.trim().toUpperCase(Locale.ROOT) : "HABIT";
        Set<String> supported = Set.of("STUDY", "LIFE", "SPORT", "HABIT");
        if (!supported.contains(resolved)) {
            return "HABIT";
        }
        return resolved;
    }

    private int resolveHour(JsonNode source, String fieldName, int defaultValue) {
        JsonNode timeRange = source == null ? null : source.get("timeRange");
        JsonNode field = timeRange == null ? null : timeRange.get(fieldName);
        if (field == null || field.isNull()) {
            field = source == null ? null : source.get(fieldName);
        }
        int value = field == null || field.isNull() ? defaultValue : field.asInt(defaultValue);
        if (value < 6 || value > 22) {
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
        int value = requiredCountNode == null || requiredCountNode.isNull() ? 1 : requiredCountNode.asInt(1);
        return Math.max(1, value);
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
}
