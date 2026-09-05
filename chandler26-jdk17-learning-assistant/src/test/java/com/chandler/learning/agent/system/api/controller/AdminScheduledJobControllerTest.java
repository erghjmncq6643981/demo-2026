package com.chandler.learning.agent.system.api.controller;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.system.api.request.AdminScheduledJobTriggerRequest;
import com.chandler.learning.agent.system.api.response.AdminScheduledJobResponse;
import com.chandler.learning.agent.system.application.AdminScheduledJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminScheduledJobControllerTest {

    private CurrentUserContext currentUserContext;
    private AdminScheduledJobService scheduledJobService;
    private AdminScheduledJobController controller;
    private LearningUser adminUser;

    @BeforeEach
    void setUp() {
        currentUserContext = mock(CurrentUserContext.class);
        scheduledJobService = mock(AdminScheduledJobService.class);
        controller = new AdminScheduledJobController(currentUserContext, scheduledJobService);

        adminUser = new LearningUser();
        adminUser.setId(9001L);
        adminUser.setRoleCode("ADMIN");
        adminUser.setEnabled(true);
        when(currentUserContext.requireUser()).thenReturn(adminUser);
    }

    @Test
    void listCallsService() {
        AdminScheduledJobResponse job = new AdminScheduledJobResponse();
        job.setJobKey("audio_sync");
        job.setName("音频同步");
        when(scheduledJobService.listJobs()).thenReturn(List.of(job));

        List<AdminScheduledJobResponse> result = controller.list();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getJobKey()).isEqualTo("audio_sync");
        verify(scheduledJobService).listJobs();
    }

    @Test
    void triggerCallsServiceWithAdminUser() {
        AdminScheduledJobResponse expected = new AdminScheduledJobResponse();
        expected.setJobKey("audio_sync");
        expected.setRunning(true);
        when(scheduledJobService.triggerJob(adminUser, "audio_sync", true)).thenReturn(expected);

        AdminScheduledJobResponse result = controller.trigger("audio_sync", true, new AdminScheduledJobTriggerRequest(true));
        assertThat(result.getRunning()).isTrue();
        verify(scheduledJobService).triggerJob(adminUser, "audio_sync", true);
    }
}
