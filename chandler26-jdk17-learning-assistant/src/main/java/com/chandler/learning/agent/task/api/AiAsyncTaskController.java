package com.chandler.learning.agent.task.api;

import com.chandler.learning.agent.task.api.AiAsyncTaskResponse;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.chandler.learning.agent.identity.domain.UserRole;

/**
 * 个人任务中心接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/ai-tasks")
@Tag(name = "AI 任务中心")
public class AiAsyncTaskController {

    private final AuthService authService;
    private final AiAsyncTaskService taskService;

    @GetMapping
    @Operation(summary = "分页查询 AI 异步任务（普通用户查自己，管理员可查全部）")
    public AiAsyncTaskPageResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(defaultValue = "false") Boolean all) {
        LearningUser user = authService.requireUser(authorization);
        if (Boolean.TRUE.equals(all) && UserRole.ADMIN.getCode().equals(user.getRoleCode())) {
            return taskService.pageAll(status, page, pageSize);
        }
        return taskService.page(user.getId(), status, page, pageSize);
    }

    @GetMapping("/admin")
    @Operation(summary = "管理员分页查询全部用户 AI 异步任务")
    public AiAsyncTaskPageResponse adminList(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        authService.requireAdmin(authorization);
        return taskService.pageAll(status, page, pageSize);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查看 AI 异步任务详情")
    public AiAsyncTaskResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long taskId) {
        LearningUser user = authService.requireUser(authorization);
        if (UserRole.ADMIN.getCode().equals(user.getRoleCode())) {
            return taskService.toDetailResponse(taskService.requireAny(taskId));
        }
        return taskService.toDetailResponse(taskService.require(user.getId(), taskId));
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "取消等待或运行中的 AI 任务")
    public AiAsyncTaskResponse cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long taskId) {
        LearningUser user = authService.requireUser(authorization);
        if (UserRole.ADMIN.getCode().equals(user.getRoleCode())) {
            return taskService.toResponse(taskService.cancelAsAdmin(user.getId(), taskId));
        }
        return taskService.toResponse(taskService.cancel(user.getId(), taskId));
    }

    @PostMapping("/{taskId}/retry")
    @Operation(summary = "重试失败 AI 异步任务")
    public AiAsyncTaskResponse retry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long taskId) {
        LearningUser user = authService.requireUser(authorization);
        if (UserRole.ADMIN.getCode().equals(user.getRoleCode())) {
            return taskService.toResponse(taskService.retryAsAdmin(user.getId(), taskId));
        }
        return taskService.toResponse(taskService.retry(user.getId(), taskId));
    }

    @PostMapping("/{taskId}/admin-retry")
    @Operation(summary = "管理员代任务归属人从失败步骤继续")
    public AiAsyncTaskResponse adminRetry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long taskId) {
        LearningUser operator = authService.requireAdmin(authorization);
        return taskService.toResponse(taskService.retryAsAdmin(operator.getId(), taskId));
    }

    @PostMapping("/{taskId}/run-now")
    @Operation(summary = "立即执行预约 AI 异步任务")
    public AiAsyncTaskResponse runNow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long taskId) {
        LearningUser user = authService.requireUser(authorization);
        if (UserRole.ADMIN.getCode().equals(user.getRoleCode())) {
            return taskService.toResponse(taskService.runNowAsAdmin(user.getId(), taskId));
        }
        return taskService.toResponse(taskService.runNow(user.getId(), taskId));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{taskId}")
    @Operation(summary = "删除 AI 异步任务")
    public void delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long taskId) {
        LearningUser user = authService.requireUser(authorization);
        if (UserRole.ADMIN.getCode().equals(user.getRoleCode())) {
            taskService.deleteAsAdmin(user.getId(), taskId);
            return;
        }
        taskService.delete(user.getId(), taskId);
    }
}
