package com.chandler.motivation.service;

import com.chandler.motivation.domain.dataobject.MotivationTask;
import com.chandler.motivation.domain.dataobject.MotivationTaskRecord;
import com.chandler.motivation.domain.dto.calendar.CalendarEventResponse;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final MotivationChildService childService;
    private final MotivationTaskService taskService;
    private final MotivationTaskRecordService taskRecordService;
    private final ObjectMapper objectMapper;

    public List<CalendarEventResponse> monthView(Long childId, Integer year, Integer month, Long userId) {
        LocalDate today = LocalDate.now();
        int resolvedYear = year == null ? today.getYear() : year;
        int resolvedMonth = month == null ? today.getMonthValue() : month;
        YearMonth yearMonth = YearMonth.of(resolvedYear, resolvedMonth);
        return rangeView(childId, yearMonth.atDay(1), yearMonth.atEndOfMonth(), userId);
    }

    public List<CalendarEventResponse> rangeView(Long childId, LocalDate startDate, LocalDate endDate, Long userId) {
        childService.requireViewAccess(childId, userId);
        LocalDate resolvedStart = startDate == null ? LocalDate.now().withDayOfMonth(1) : startDate;
        LocalDate resolvedEnd = endDate == null ? YearMonth.from(resolvedStart).atEndOfMonth() : endDate;
        List<MotivationTask> tasks = taskService.listByChild(childId, userId).stream()
                .filter(task -> MotivationEnums.codeEquals(MotivationEnums.TaskStatus.ACTIVE, task.getStatus()))
                .toList();
        Map<String, MotivationTaskRecord> recordMap = taskRecordService.listByChildAndRange(childId, resolvedStart, resolvedEnd)
                .stream()
                .collect(Collectors.toMap(this::recordKey, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        List<CalendarEventResponse> events = new ArrayList<>();
        for (LocalDate date = resolvedStart; !date.isAfter(resolvedEnd); date = date.plusDays(1)) {
            for (MotivationTask task : tasks) {
                if (!isScheduled(task, date)) {
                    continue;
                }
                MotivationTaskRecord record = recordMap.get(task.getId() + ":" + date);
                events.add(record == null ? toPlannedEvent(task, date) : toRecordEvent(record));
            }
        }
        return events;
    }

    private boolean isScheduled(MotivationTask task, LocalDate date) {
        JsonNode schedule = readSchedule(task.getScheduleJson());
        if (MotivationEnums.codeEquals(MotivationEnums.PeriodType.WEEKLY, task.getPeriodType())) {
            return isWeeklyScheduled(schedule, date.getDayOfWeek());
        }
        if (MotivationEnums.codeEquals(MotivationEnums.PeriodType.MONTHLY, task.getPeriodType())) {
            return isMonthlyScheduled(schedule, date);
        }
        return true;
    }

    private boolean isWeeklyScheduled(JsonNode schedule, DayOfWeek dayOfWeek) {
        JsonNode days = schedule == null ? null : schedule.get("days");
        if (days == null || !days.isArray() || days.isEmpty()) {
            return dayOfWeek == DayOfWeek.MONDAY;
        }
        int value = dayOfWeek.getValue();
        for (JsonNode day : days) {
            if (day.asInt() == value) {
                return true;
            }
        }
        return false;
    }

    private boolean isMonthlyScheduled(JsonNode schedule, LocalDate date) {
        JsonNode days = schedule == null ? null : schedule.get("days");
        if (days == null || !days.isArray() || days.isEmpty()) {
            return date.equals(date.with(TemporalAdjusters.firstDayOfMonth()));
        }
        for (JsonNode day : days) {
            if (day.asInt() == date.getDayOfMonth()) {
                return true;
            }
        }
        return false;
    }

    private JsonNode readSchedule(String scheduleJson) {
        try {
            return objectMapper.readTree(scheduleJson == null || scheduleJson.isBlank() ? "{}" : scheduleJson);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String readText(JsonNode source, String fieldName) {
        if (source == null || source.isNull()) {
            return "";
        }
        JsonNode field = source.get(fieldName);
        return field == null || field.isNull() ? "" : field.asText("");
    }

    private CalendarEventResponse toPlannedEvent(MotivationTask task, LocalDate date) {
        CalendarEventResponse response = new CalendarEventResponse();
        response.setTaskId(task.getId());
        response.setGoalId(task.getGoalId());
        response.setChildId(task.getChildId());
        response.setTaskDate(date);
        response.setTaskName(task.getName());
        response.setTaskColor(task.getTaskColor());
        response.setPointType(task.getPointType());
        response.setPointColor(task.getPointColor());
        response.setBasePoints(task.getBasePoints());
        response.setPeriodType(task.getPeriodType());
        response.setScheduleJson(task.getScheduleJson());
        response.setCompletionProgress(MotivationConstants.Schedule.EMPTY_PROGRESS);
        response.setStatus(MotivationEnums.TaskStatus.PENDING.code());
        response.setScoreAwarded(MotivationConstants.Schedule.EMPTY_PROGRESS);
        response.setPersisted(false);
        return response;
    }

    private CalendarEventResponse toRecordEvent(MotivationTaskRecord record) {
        CalendarEventResponse response = new CalendarEventResponse();
        response.setRecordId(record.getId());
        response.setTaskId(record.getTaskId());
        response.setGoalId(record.getGoalId());
        response.setChildId(record.getChildId());
        response.setTaskDate(record.getTaskDate());
        response.setTaskName(record.getTaskNameSnapshot());
        response.setTaskColor(record.getTaskColorSnapshot());
        response.setPointType(record.getPointTypeSnapshot());
        response.setPointColor(record.getPointColorSnapshot());
        response.setBasePoints(record.getBasePointsSnapshot());
        response.setPeriodType(readText(readSchedule(record.getScheduleSnapshotJson()), "type"));
        response.setScheduleJson(record.getScheduleSnapshotJson());
        response.setCompletionProgress(record.getCompletionProgress());
        response.setStatus(record.getStatus());
        response.setScoreAwarded(record.getScoreAwarded());
        response.setPersisted(true);
        response.setSubmittedAt(record.getSubmittedAt());
        response.setReviewedAt(record.getReviewedAt());
        return response;
    }

    private String recordKey(MotivationTaskRecord record) {
        return record.getTaskId() + ":" + record.getTaskDate();
    }
}
