package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationTask;
import com.chandler.motivation.domain.dataobject.MotivationTaskRecord;
import com.chandler.motivation.domain.dto.task.TaskCompleteRequest;
import com.chandler.motivation.domain.dto.task.TaskReviewRequest;
import com.chandler.motivation.domain.mapper.MotivationTaskRecordMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MotivationTaskRecordService extends ServiceImpl<MotivationTaskRecordMapper, MotivationTaskRecord> {

    private final MotivationTaskService taskService;
    private final MotivationChildService childService;
    private final MotivationPointLedgerService pointLedgerService;
    private final MotivationSystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    public MotivationTaskRecord findByTaskAndDate(Long taskId, LocalDate taskDate) {
        return getOne(new LambdaQueryWrapper<MotivationTaskRecord>()
                .eq(MotivationTaskRecord::getTaskId, taskId)
                .eq(MotivationTaskRecord::getTaskDate, taskDate)
                .last("limit 1"));
    }

    public MotivationTaskRecord createOrUpdateSnapshot(MotivationTaskRecord record) {
        MotivationTaskRecord existing = findByTaskAndDate(record.getTaskId(), record.getTaskDate());
        if (existing != null) {
            record.setId(existing.getId());
            updateById(record);
            return record;
        }
        save(record);
        return record;
    }

    public List<MotivationTaskRecord> listByChildAndRange(Long childId, LocalDate startDate, LocalDate endDate) {
        return list(new LambdaQueryWrapper<MotivationTaskRecord>()
                .eq(MotivationTaskRecord::getChildId, childId)
                .eq(MotivationTaskRecord::getDeleted, 0)
                .ge(startDate != null, MotivationTaskRecord::getTaskDate, startDate)
                .le(endDate != null, MotivationTaskRecord::getTaskDate, endDate)
                .orderByAsc(MotivationTaskRecord::getTaskDate)
                .orderByAsc(MotivationTaskRecord::getTaskId));
    }

    @Transactional
    public MotivationTaskRecord complete(Long taskId, TaskCompleteRequest request, Long userId) {
        MotivationTask task = taskService.requireActiveTask(taskId, userId);
        LocalDate taskDate = request != null && request.getTaskDate() != null ? request.getTaskDate() : LocalDate.now();
        validateScheduledDate(task, taskDate);
        validatePeriodRequiredCount(task, taskDate);
        MotivationTaskRecord existing = findByTaskAndDate(taskId, taskDate);
        if (existing != null && MotivationConstants.TaskStatus.APPROVED.equals(existing.getStatus())) {
            throw new MotivationException("TASK_RECORD_ALREADY_APPROVED", "这一天的任务已经完成并入账");
        }

        MotivationTaskRecord record = existing == null ? new MotivationTaskRecord() : existing;
        applyTaskSnapshot(record, task, taskDate);
        record.setSourceType(MotivationConstants.UserType.CHILD);
        record.setSubmittedByUserId(userId);
        record.setSubmittedAt(LocalDateTime.now());
        record.setCompletionProgress(resolveProgress(request == null ? null : request.getCompletionProgress()));
        record.setAttachmentJson("{}");
        record.setDeleted(0);

        int scoreAwarded = calculateScore(task.getBasePoints(), record.getCompletionProgress());
        if (Integer.valueOf(1).equals(task.getRequireApproval())) {
            record.setStatus(MotivationConstants.TaskStatus.SUBMITTED);
            record.setScoreAwarded(0);
            record.setReviewedByUserId(null);
            record.setReviewedAt(null);
            record.setReviewRemark(null);
            createOrUpdateSnapshot(record);
            systemLogService.record(userId, task.getChildId(), MotivationConstants.LogType.TASK,
                    "提交任务打卡", "提交任务「" + task.getName() + "」，等待审核");
            return record;
        }

        record.setStatus(MotivationConstants.TaskStatus.APPROVED);
        record.setReviewedByUserId(userId);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewRemark("自动通过");
        record.setScoreAwarded(scoreAwarded);
        createOrUpdateSnapshot(record);
        awardTaskScore(record, userId);
        systemLogService.record(userId, task.getChildId(), MotivationConstants.LogType.TASK,
                "完成任务", "完成任务「" + task.getName() + "」，获得 " + scoreAwarded + " " + task.getPointType());
        return record;
    }

    @Transactional
    public MotivationTaskRecord approve(Long recordId, TaskReviewRequest request, Long userId) {
        MotivationTaskRecord record = requireRecord(recordId, userId);
        if (MotivationConstants.TaskStatus.APPROVED.equals(record.getStatus())) {
            return record;
        }
        if (!MotivationConstants.TaskStatus.SUBMITTED.equals(record.getStatus())) {
            throw new MotivationException("TASK_RECORD_NOT_SUBMITTED", "只有待审核任务可以通过");
        }
        validateRecordRequiredCount(record);
        record.setStatus(MotivationConstants.TaskStatus.APPROVED);
        record.setReviewedByUserId(userId);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewRemark(request == null ? null : request.getRemark());
        record.setScoreAwarded(calculateScore(record.getBasePointsSnapshot(), record.getCompletionProgress()));
        updateById(record);
        awardTaskScore(record, userId);
        systemLogService.record(userId, record.getChildId(), MotivationConstants.LogType.TASK,
                "审核通过任务", "任务「" + record.getTaskNameSnapshot() + "」审核通过");
        return record;
    }

    @Transactional
    public MotivationTaskRecord reject(Long recordId, TaskReviewRequest request, Long userId) {
        MotivationTaskRecord record = requireRecord(recordId, userId);
        if (!MotivationConstants.TaskStatus.SUBMITTED.equals(record.getStatus())) {
            throw new MotivationException("TASK_RECORD_NOT_SUBMITTED", "只有待审核任务可以拒绝");
        }
        record.setStatus(MotivationConstants.TaskStatus.REJECTED);
        record.setReviewedByUserId(userId);
        record.setReviewedAt(LocalDateTime.now());
        record.setReviewRemark(request == null ? null : request.getRemark());
        record.setScoreAwarded(0);
        updateById(record);
        systemLogService.record(userId, record.getChildId(), MotivationConstants.LogType.TASK,
                "审核拒绝任务", "任务「" + record.getTaskNameSnapshot() + "」审核未通过");
        return record;
    }

    private MotivationTaskRecord requireRecord(Long recordId, Long userId) {
        MotivationTaskRecord record = getById(recordId);
        if (record == null || Integer.valueOf(1).equals(record.getDeleted())) {
            throw new MotivationException("TASK_RECORD_NOT_FOUND", "任务记录不存在");
        }
        childService.requireManageAccess(record.getChildId(), userId);
        return record;
    }

    private void applyTaskSnapshot(MotivationTaskRecord record, MotivationTask task, LocalDate taskDate) {
        record.setTaskId(task.getId());
        record.setGoalId(task.getGoalId());
        record.setChildId(task.getChildId());
        record.setTaskNameSnapshot(task.getName());
        record.setTaskColorSnapshot(task.getTaskColor());
        record.setPointTypeSnapshot(task.getPointType());
        record.setPointColorSnapshot(task.getPointColor());
        record.setBasePointsSnapshot(task.getBasePoints());
        record.setScheduleSnapshotJson(task.getScheduleJson());
        record.setRuleSnapshotJson("{}");
        record.setTaskDate(taskDate);
    }

    private int resolveProgress(Integer progress) {
        int resolved = progress == null ? 100 : progress;
        return Math.max(0, Math.min(100, resolved));
    }

    private int calculateScore(Integer basePoints, Integer completionProgress) {
        int base = basePoints == null ? 0 : basePoints;
        int progress = completionProgress == null ? 100 : completionProgress;
        return Math.max(0, Math.round(base * (progress / 100.0f)));
    }

    private void validateScheduledDate(MotivationTask task, LocalDate taskDate) {
        JsonNode schedule = readSchedule(task.getScheduleJson());
        if (MotivationConstants.PeriodType.WEEKLY.equals(task.getPeriodType())
                && !containsDay(schedule.get("days"), taskDate.getDayOfWeek().getValue())) {
            throw new MotivationException("TASK_NOT_SCHEDULED_DATE", "这一天不在任务可完成日期内");
        }
        if (MotivationConstants.PeriodType.MONTHLY.equals(task.getPeriodType())
                && !containsDay(schedule.get("days"), taskDate.getDayOfMonth())) {
            throw new MotivationException("TASK_NOT_SCHEDULED_DATE", "这一天不在任务可完成日期内");
        }
    }

    private void validatePeriodRequiredCount(MotivationTask task, LocalDate taskDate) {
        if (MotivationConstants.PeriodType.DAILY.equals(task.getPeriodType())) {
            return;
        }
        JsonNode schedule = readSchedule(task.getScheduleJson());
        int requiredCount = Math.max(1, schedule.path("requiredCount").asInt(1));
        MotivationTaskRecord existing = findByTaskAndDate(task.getId(), taskDate);
        LocalDate startDate = MotivationConstants.PeriodType.WEEKLY.equals(task.getPeriodType())
                ? taskDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                : taskDate.withDayOfMonth(1);
        LocalDate endDate = MotivationConstants.PeriodType.WEEKLY.equals(task.getPeriodType())
                ? taskDate.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                : taskDate.withDayOfMonth(taskDate.lengthOfMonth());
        long approvedCount = count(new LambdaQueryWrapper<MotivationTaskRecord>()
                .eq(MotivationTaskRecord::getTaskId, task.getId())
                .eq(MotivationTaskRecord::getDeleted, 0)
                .eq(MotivationTaskRecord::getStatus, MotivationConstants.TaskStatus.APPROVED)
                .ne(existing != null && existing.getId() != null, MotivationTaskRecord::getId, existing == null ? null : existing.getId())
                .ge(MotivationTaskRecord::getTaskDate, startDate)
                .le(MotivationTaskRecord::getTaskDate, endDate));
        if (approvedCount >= requiredCount) {
            throw new MotivationException("TASK_PERIOD_ALREADY_COMPLETED", "这个周期的完成次数已达标");
        }
    }

    private boolean containsDay(JsonNode days, int value) {
        if (days == null || !days.isArray() || days.isEmpty()) {
            return true;
        }
        for (JsonNode day : days) {
            if (day.asInt() == value) {
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

    private void validateRecordRequiredCount(MotivationTaskRecord record) {
        JsonNode schedule = readSchedule(record.getScheduleSnapshotJson());
        String periodType = schedule.path("type").asText("");
        if (!MotivationConstants.PeriodType.WEEKLY.equals(periodType)
                && !MotivationConstants.PeriodType.MONTHLY.equals(periodType)) {
            return;
        }
        int requiredCount = Math.max(1, schedule.path("requiredCount").asInt(1));
        LocalDate taskDate = record.getTaskDate();
        LocalDate startDate = MotivationConstants.PeriodType.WEEKLY.equals(periodType)
                ? taskDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                : taskDate.withDayOfMonth(1);
        LocalDate endDate = MotivationConstants.PeriodType.WEEKLY.equals(periodType)
                ? taskDate.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                : taskDate.withDayOfMonth(taskDate.lengthOfMonth());
        long approvedCount = count(new LambdaQueryWrapper<MotivationTaskRecord>()
                .eq(MotivationTaskRecord::getTaskId, record.getTaskId())
                .eq(MotivationTaskRecord::getDeleted, 0)
                .eq(MotivationTaskRecord::getStatus, MotivationConstants.TaskStatus.APPROVED)
                .ne(record.getId() != null, MotivationTaskRecord::getId, record.getId())
                .ge(MotivationTaskRecord::getTaskDate, startDate)
                .le(MotivationTaskRecord::getTaskDate, endDate));
        if (approvedCount >= requiredCount) {
            throw new MotivationException("TASK_PERIOD_ALREADY_COMPLETED", "这个周期的完成次数已达标");
        }
    }

    private void awardTaskScore(MotivationTaskRecord record, Long userId) {
        if (record.getScoreAwarded() == null || record.getScoreAwarded() <= 0) {
            return;
        }
        pointLedgerService.applyChange(record.getChildId(),
                record.getPointTypeSnapshot(),
                record.getScoreAwarded(),
                MotivationConstants.LedgerSourceType.TASK_RECORD,
                record.getId(),
                record.getTaskNameSnapshot(),
                "任务完成入账",
                userId);
    }
}
