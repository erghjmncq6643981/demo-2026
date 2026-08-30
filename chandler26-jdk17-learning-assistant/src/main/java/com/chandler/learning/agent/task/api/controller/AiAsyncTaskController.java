package com.chandler.learning.agent.task.api.controller;

import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskPageResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人任务中心接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/ai-tasks")
@Tag(name = "AI 任务中心")
public class AiAsyncTaskController {

    private final CurrentUserContext currentUserContext;
    private final AiAsyncTaskService taskService;

    /** 分页查询 AI 异步任务（普通用户查自己，管理员可查全部）。 */
    @GetMapping
    @Operation(summary = "分页查询 AI 异步任务（普通用户查自己，管理员可查全部）")
    public AiAsyncTaskPageResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(defaultValue = "false") Boolean all) {
        LearningUser user = currentUserContext.requireUser();
        if (Boolean.TRUE.equals(all) && currentUserContext.hasPermission(user, LearningPermission.SYSTEM_ADMIN)) {
            return taskService.pageAll(status, page, pageSize);
        }
        return taskService.page(user.getId(), status, page, pageSize);
    }

    /** 管理员分页查询全部用户 AI 异步任务。 */
    @GetMapping("/admin")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "管理员分页查询全部用户 AI 异步任务")
    public AiAsyncTaskPageResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return taskService.pageAll(status, page, pageSize);
    }

    /** 查看 AI 异步任务详情。 */
    @GetMapping("/{taskId}")
    @Operation(summary = "查看 AI 异步任务详情")
    public AiAsyncTaskResponse detail(
            @PathVariable Long taskId) {
        LearningUser user = currentUserContext.requireUser();
        if (currentUserContext.hasPermission(user, LearningPermission.SYSTEM_ADMIN)) {
            return taskService.toDetailResponse(taskService.requireAny(taskId));
        }
        return taskService.toDetailResponse(taskService.require(user.getId(), taskId));
    }

    /** 取消等待或运行中的 AI 任务。 */
    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "取消等待或运行中的 AI 任务")
    public AiAsyncTaskResponse cancel(
            @PathVariable Long taskId) {
        LearningUser user = currentUserContext.requireUser();
        if (currentUserContext.hasPermission(user, LearningPermission.SYSTEM_ADMIN)) {
            return taskService.toResponse(taskService.cancelAsAdmin(user.getId(), taskId));
        }
        return taskService.toResponse(taskService.cancel(user.getId(), taskId));
    }

    /** 重试失败 AI 异步任务。 */
    @PostMapping("/{taskId}/retry")
    @Operation(summary = "重试失败 AI 异步任务")
    public AiAsyncTaskResponse retry(
            @PathVariable Long taskId) {
        LearningUser user = currentUserContext.requireUser();
        if (currentUserContext.hasPermission(user, LearningPermission.SYSTEM_ADMIN)) {
            return taskService.toResponse(taskService.retryAsAdmin(user.getId(), taskId));
        }
        return taskService.toResponse(taskService.retry(user.getId(), taskId));
    }

    /** 管理员代任务归属人从失败步骤继续。 */
    @PostMapping("/{taskId}/admin-retry")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "管理员代任务归属人从失败步骤继续")
    public AiAsyncTaskResponse adminRetry(
            @PathVariable Long taskId) {
        LearningUser operator = currentUserContext.requireUser();
        return taskService.toResponse(taskService.retryAsAdmin(operator.getId(), taskId));
    }

    /** 立即执行预约 AI 异步任务。 */
    @PostMapping("/{taskId}/run-now")
    @Operation(summary = "立即执行预约 AI 异步任务")
    public AiAsyncTaskResponse runNow(
            @PathVariable Long taskId) {
        LearningUser user = currentUserContext.requireUser();
        if (currentUserContext.hasPermission(user, LearningPermission.SYSTEM_ADMIN)) {
            return taskService.toResponse(taskService.runNowAsAdmin(user.getId(), taskId));
        }
        return taskService.toResponse(taskService.runNow(user.getId(), taskId));
    }

    /** 删除 AI 异步任务。 */
    @org.springframework.web.bind.annotation.DeleteMapping("/{taskId}")
    @Operation(summary = "删除 AI 异步任务")
    public void delete(
            @PathVariable Long taskId) {
        LearningUser user = currentUserContext.requireUser();
        if (currentUserContext.hasPermission(user, LearningPermission.SYSTEM_ADMIN)) {
            taskService.deleteAsAdmin(user.getId(), taskId);
            return;
        }
        taskService.delete(user.getId(), taskId);
    }
}
