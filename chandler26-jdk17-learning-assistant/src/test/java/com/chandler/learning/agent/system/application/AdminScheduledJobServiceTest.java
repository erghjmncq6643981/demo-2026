package com.chandler.learning.agent.system.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.system.api.response.AdminScheduledJobResponse;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.task.application.AiAsyncTaskScheduler;
import com.chandler.learning.agent.vocabulary.application.VocabularyAudioSyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminScheduledJobServiceTest {

    private SystemLogService systemLogService;
    private VocabularyAudioSyncScheduler vocabularyAudioSyncScheduler;
    private SystemLogOutboxPersistenceService outboxPersistenceService;
    private AiAsyncTaskScheduler aiAsyncTaskScheduler;
    private AdminScheduledJobService jobService;

    @BeforeEach
    void setUp() {
        systemLogService = mock(SystemLogService.class);
        vocabularyAudioSyncScheduler = mock(VocabularyAudioSyncScheduler.class);
        outboxPersistenceService = mock(SystemLogOutboxPersistenceService.class);
        aiAsyncTaskScheduler = mock(AiAsyncTaskScheduler.class);

        jobService = new AdminScheduledJobService(
                new SyncTaskExecutor(),
                systemLogService,
                vocabularyAudioSyncScheduler,
                outboxPersistenceService,
                aiAsyncTaskScheduler,
                "0 0 3 * * ?",
                "30000",
                "5000"
        );
    }

    @Test
    void listJobsReturnsPredefinedJobs() {
        List<AdminScheduledJobResponse> jobs = jobService.listJobs();
        assertThat(jobs).isNotEmpty();
        assertThat(jobs).extracting(AdminScheduledJobResponse::getJobKey)
                .contains(AdminScheduledJobService.JOB_AUDIO_SYNC,
                        AdminScheduledJobService.JOB_SYSTEM_LOG_RECOVERY,
                        AdminScheduledJobService.JOB_AI_TASK_DISPATCH);
    }

    @Test
    void triggerJobThrowsNotFoundForUnknownJob() {
        LearningUser operator = new LearningUser();
        operator.setId(1L);

        assertThatThrownBy(() -> jobService.triggerJob(operator, "non_existent_job", true))
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo(LearningErrorCode.JOB_NOT_FOUND.getCode());
    }

    @Test
    void triggerAudioSyncJobSynchronouslyExecutesAndLogs() {
        LearningUser operator = new LearningUser();
        operator.setId(1001L);

        when(vocabularyAudioSyncScheduler.syncMissingAudio()).thenReturn(
                new VocabularyAudioSyncScheduler.AudioSyncResult(3000, 50, 100, 10, 2, 2, 500L)
        );

        AdminScheduledJobResponse response = jobService.triggerJob(operator, AdminScheduledJobService.JOB_AUDIO_SYNC, false);

        assertThat(response).isNotNull();
        assertThat(response.getJobKey()).isEqualTo(AdminScheduledJobService.JOB_AUDIO_SYNC);
        assertThat(response.getLastStatus()).isEqualTo("SUCCESS");
        assertThat(response.getLastSummary()).contains("扫描=3000", "补全=100", "合成=2");
        assertThat(response.getRunning()).isFalse();

        verify(systemLogService).record(eq(1001L), eq(SystemLogType.SYSTEM), eq("管理员手动触发定时任务"), anyString());
        verify(vocabularyAudioSyncScheduler).syncMissingAudio();
    }

    @Test
    void triggerSystemLogRecoveryJobExecutes() {
        LearningUser operator = new LearningUser();
        operator.setId(1001L);

        when(outboxPersistenceService.persistPendingBatch(100)).thenReturn(5);

        AdminScheduledJobResponse response = jobService.triggerJob(operator, AdminScheduledJobService.JOB_SYSTEM_LOG_RECOVERY, false);

        assertThat(response.getLastStatus()).isEqualTo("SUCCESS");
        assertThat(response.getLastSummary()).contains("5");
        verify(outboxPersistenceService).persistPendingBatch(100);
    }
}
