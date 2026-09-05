package com.chandler.learning.agent.system.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.system.api.response.AdminScheduledJobResponse;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.task.application.AiAsyncTaskScheduler;
import com.chandler.learning.agent.vocabulary.application.VocabularyAudioSyncScheduler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 后台定时任务管理应用服务。
 * <p>
 * 统一管理系统后台 JOB 的元数据、实时执行状态、并发排他锁、手动触发及审计日志记录。
 * </p>
 */
@Slf4j
@Service
public class AdminScheduledJobService {

    public static final String JOB_AUDIO_SYNC = "audio_sync";
    public static final String JOB_SYSTEM_LOG_RECOVERY = "system_log_recovery";
    public static final String JOB_AI_TASK_DISPATCH = "ai_task_dispatch";

    private final Executor aiTaskExecutor;
    private final SystemLogService systemLogService;
    private final VocabularyAudioSyncScheduler vocabularyAudioSyncScheduler;
    private final SystemLogOutboxPersistenceService systemLogOutboxPersistenceService;
    private final AiAsyncTaskScheduler aiAsyncTaskScheduler;

    private final String audioSyncCron;
    private final String logRecoveryDelayMs;
    private final String aiTaskPollIntervalMs;

    private final Map<String, JobDefinition> jobRegistry = new ConcurrentHashMap<>();
    private final Map<String, JobRuntimeState> runtimeRegistry = new ConcurrentHashMap<>();

    public AdminScheduledJobService(
            @Qualifier("aiTaskExecutor") Executor aiTaskExecutor,
            SystemLogService systemLogService,
            VocabularyAudioSyncScheduler vocabularyAudioSyncScheduler,
            SystemLogOutboxPersistenceService systemLogOutboxPersistenceService,
            AiAsyncTaskScheduler aiAsyncTaskScheduler,
            @Value("${learning.audio.sync-cron:0 0 3 * * ?}") String audioSyncCron,
            @Value("${learning.audit-log.recovery-delay-ms:30000}") String logRecoveryDelayMs,
            @Value("${learning.ai-task.poll-interval-ms:5000}") String aiTaskPollIntervalMs) {
        this.aiTaskExecutor = aiTaskExecutor;
        this.systemLogService = systemLogService;
        this.vocabularyAudioSyncScheduler = vocabularyAudioSyncScheduler;
        this.systemLogOutboxPersistenceService = systemLogOutboxPersistenceService;
        this.aiAsyncTaskScheduler = aiAsyncTaskScheduler;
        this.audioSyncCron = audioSyncCron;
        this.logRecoveryDelayMs = logRecoveryDelayMs;
        this.aiTaskPollIntervalMs = aiTaskPollIntervalMs;

        initJobRegistry();
    }

    private void initJobRegistry() {
        registerJob(new JobDefinition(
                JOB_AUDIO_SYNC,
                "音频资源缺省同步 (词汇+场景文章TTS)",
                "核验全量词汇库有道发音与场景文章阿里云 TTS AI 朗读音频文件，缺失时自动补全",
                audioSyncCron,
                () -> {
                    var result = vocabularyAudioSyncScheduler.syncMissingAudio();
                    return String.format("词汇[扫描=%d, 缺省=%d, 补全=%d], 场景文章[扫描=%d, 缺省=%d, 合成=%d]",
                            result.totalTerms(), result.missingTerms(), result.downloadedFiles(),
                            result.totalSceneUnits(), result.missingSceneUnits(), result.synthesizedSceneAudio());
                }
        ));

        registerJob(new JobDefinition(
                JOB_SYSTEM_LOG_RECOVERY,
                "系统日志 Outbox 补偿",
                "定期补偿重试尚未写入最终存储的系统日志 Outbox 待发布记录",
                "fixedDelay: " + logRecoveryDelayMs + "ms",
                () -> {
                    int persisted = systemLogOutboxPersistenceService.persistPendingBatch(100);
                    return "本次补偿持久化日志数: " + persisted;
                }
        ));

        registerJob(new JobDefinition(
                JOB_AI_TASK_DISPATCH,
                "AI 异步任务轮询分发",
                "轮询并分发处于等待执行状态的场景材料与批量词卡等 AI 异步任务",
                "fixedDelay: " + aiTaskPollIntervalMs + "ms",
                () -> {
                    aiAsyncTaskScheduler.dispatchDueTasks();
                    return "已触发到期 AI 异步任务分发检查";
                }
        ));
    }

    /**
     * 注册后台定时任务定义。
     *
     * @param jobDef 任务定义元数据
     */
    public void registerJob(JobDefinition jobDef) {
        jobRegistry.put(jobDef.jobKey(), jobDef);
        runtimeRegistry.computeIfAbsent(jobDef.jobKey(), k -> new JobRuntimeState());
    }

    /**
     * 查询所有已注册后台定时任务的实时状态。
     */
    public List<AdminScheduledJobResponse> listJobs() {
        List<AdminScheduledJobResponse> responses = new ArrayList<>();
        for (JobDefinition def : jobRegistry.values()) {
            JobRuntimeState state = runtimeRegistry.get(def.jobKey());
            responses.add(toResponse(def, state));
        }
        return responses;
    }

    /**
     * 手动触发指定后台定时任务。
     *
     * @param operator 操作管理员用户
     * @param jobKey   任务唯一标识
     * @param async    是否异步执行
     * @return 当前任务执行响应
     */
    public AdminScheduledJobResponse triggerJob(LearningUser operator, String jobKey, boolean async) {
        JobDefinition def = jobRegistry.get(jobKey);
        if (def == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.JOB_NOT_FOUND, "未找到指定的定时任务: " + jobKey);
        }

        JobRuntimeState state = runtimeRegistry.get(jobKey);
        if (!state.getRunning().compareAndSet(false, true)) {
            throw LearningAssistantException.of(LearningErrorCode.JOB_ALREADY_RUNNING);
        }

        Long operatorId = operator != null ? operator.getId() : null;
        systemLogService.record(operatorId, SystemLogType.SYSTEM, "管理员手动触发定时任务",
                String.format("任务标识=%s, 任务名称=%s, 执行模式=%s", jobKey, def.name(), async ? "异步" : "同步"));

        state.setLastRunTime(LocalDateTime.now());
        state.setLastStatus("RUNNING");

        if (async) {
            aiTaskExecutor.execute(() -> executeJobInternal(operatorId, def, state));
            return toResponse(def, state);
        } else {
            executeJobInternal(operatorId, def, state);
            return toResponse(def, state);
        }
    }

    /**
     * 供外部定时调度（如 Cron 自动触发）回调记录执行结果。
     */
    public void recordJobExecution(String jobKey, boolean success, long costMs, String summary) {
        JobRuntimeState state = runtimeRegistry.get(jobKey);
        if (state != null) {
            state.setLastRunTime(LocalDateTime.now());
            state.setLastCostMs(costMs);
            state.setLastStatus(success ? "SUCCESS" : "FAILED");
            state.setLastSummary(summary);
            state.getRunning().set(false);
        }
    }

    private void executeJobInternal(Long operatorId, JobDefinition def, JobRuntimeState state) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("【后台任务】开始手动执行 jobKey={}, name={}", def.jobKey(), def.name());
            String summary = def.action().get();
            long costMs = System.currentTimeMillis() - startTime;

            state.setLastCostMs(costMs);
            state.setLastStatus("SUCCESS");
            state.setLastSummary(summary);

            log.info("【后台任务】执行成功 jobKey={}, cost={}ms, summary={}", def.jobKey(), costMs, summary);
            systemLogService.record(operatorId, SystemLogType.SYSTEM, "定时任务执行成功",
                    String.format("任务=%s, 耗时=%dms, 摘要=%s", def.name(), costMs, summary));
        } catch (Throwable ex) {
            long costMs = System.currentTimeMillis() - startTime;
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

            state.setLastCostMs(costMs);
            state.setLastStatus("FAILED");
            state.setLastSummary("执行失败: " + errorMsg);

            log.warn("【后台任务】执行失败 jobKey={}, cost={}ms, error={}", def.jobKey(), costMs, errorMsg, ex);
            systemLogService.record(operatorId, SystemLogType.ERROR, "定时任务执行失败",
                    String.format("任务=%s, 耗时=%dms, 错误=%s", def.name(), costMs, errorMsg));
        } finally {
            state.getRunning().set(false);
        }
    }

    private AdminScheduledJobResponse toResponse(JobDefinition def, JobRuntimeState state) {
        return new AdminScheduledJobResponse(
                def.jobKey(),
                def.name(),
                def.description(),
                def.cronExpression(),
                state != null && state.getRunning().get(),
                state != null ? state.getLastRunTime() : null,
                state != null ? state.getLastCostMs() : null,
                state != null ? state.getLastStatus() : "IDLE",
                state != null ? state.getLastSummary() : null
        );
    }

    record JobDefinition(
            String jobKey,
            String name,
            String description,
            String cronExpression,
            Supplier<String> action
    ) {}

    @Getter
    static class JobRuntimeState {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private volatile LocalDateTime lastRunTime;
        private volatile Long lastCostMs;
        private volatile String lastStatus = "IDLE";
        private volatile String lastSummary;

        void setLastRunTime(LocalDateTime lastRunTime) {
            this.lastRunTime = lastRunTime;
        }

        void setLastCostMs(Long lastCostMs) {
            this.lastCostMs = lastCostMs;
        }

        void setLastStatus(String lastStatus) {
            this.lastStatus = lastStatus;
        }

        void setLastSummary(String lastSummary) {
            this.lastSummary = lastSummary;
        }
    }
}
