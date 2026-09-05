package com.chandler.learning.agent.system.api.controller;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
import com.chandler.learning.agent.system.api.request.AdminScheduledJobTriggerRequest;
import com.chandler.learning.agent.system.api.response.AdminScheduledJobResponse;
import com.chandler.learning.agent.system.application.AdminScheduledJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台定时任务管理与手动触发控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/jobs")
@RequirePermission(LearningPermission.SYSTEM_ADMIN)
@Tag(name = "系统管理：定时任务")
public class AdminScheduledJobController {

    private final CurrentUserContext currentUserContext;
    private final AdminScheduledJobService scheduledJobService;

    /**
     * 查询所有后台定时任务状态。
     */
    @GetMapping
    @Operation(summary = "查询后台定时任务列表与状态")
    public List<AdminScheduledJobResponse> list() {
        return scheduledJobService.listJobs();
    }

    /**
     * 手动触发指定后台定时任务。
     */
    @PostMapping("/{jobKey}/trigger")
    @Operation(summary = "手动触发后台定时任务")
    public AdminScheduledJobResponse trigger(
            @PathVariable String jobKey,
            @RequestParam(required = false, defaultValue = "true") Boolean async,
            @RequestBody(required = false) AdminScheduledJobTriggerRequest request) {
        LearningUser operator = currentUserContext.requireUser();
        boolean isAsync = (request != null && request.getAsync() != null) ? request.getAsync() : Boolean.TRUE.equals(async);
        return scheduledJobService.triggerJob(operator, jobKey, isAsync);
    }
}
