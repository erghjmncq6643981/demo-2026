package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationTask;
import com.chandler.motivation.domain.dataobject.MotivationTaskRecord;
import com.chandler.motivation.domain.dto.task.TaskCompleteRequest;
import com.chandler.motivation.domain.dto.task.TaskReviewRequest;
import com.chandler.motivation.domain.dto.task.TaskSaveRequest;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationTaskRecordService;
import com.chandler.motivation.service.MotivationTaskService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final MotivationTaskService taskService;
    private final MotivationTaskRecordService taskRecordService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<MotivationTask>> list(@RequestParam Long childId) {
        return ApiResponse.ok(taskService.listByChild(childId, authService.requireUser().getId()));
    }

    @PostMapping
    public ApiResponse<MotivationTask> create(@Valid @RequestBody TaskSaveRequest request) {
        return ApiResponse.ok(taskService.create(request, authService.requireUser().getId()));
    }

    @PutMapping("/{taskId}")
    public ApiResponse<MotivationTask> update(@PathVariable Long taskId, @Valid @RequestBody TaskSaveRequest request) {
        return ApiResponse.ok(taskService.update(taskId, request, authService.requireUser().getId()));
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Boolean> delete(@PathVariable Long taskId) {
        taskService.delete(taskId, authService.requireUser().getId());
        return ApiResponse.ok(Boolean.TRUE);
    }

    @PostMapping("/{taskId}/complete")
    public ApiResponse<MotivationTaskRecord> complete(@PathVariable Long taskId, @Valid @RequestBody TaskCompleteRequest request) {
        return ApiResponse.ok(taskRecordService.complete(taskId, request, authService.requireUser().getId()));
    }

    @PostMapping("/records/{recordId}/approve")
    public ApiResponse<MotivationTaskRecord> approve(@PathVariable Long recordId,
                                                     @RequestBody(required = false) TaskReviewRequest request) {
        return ApiResponse.ok(taskRecordService.approve(recordId, request, authService.requireUser().getId()));
    }

    @PostMapping("/records/{recordId}/reject")
    public ApiResponse<MotivationTaskRecord> reject(@PathVariable Long recordId,
                                                    @RequestBody(required = false) TaskReviewRequest request) {
        return ApiResponse.ok(taskRecordService.reject(recordId, request, authService.requireUser().getId()));
    }
}
