package com.chandler.learning.agent.controller.learning;

import com.chandler.learning.agent.domain.dto.learning.SystemLogRequest;
import com.chandler.learning.agent.domain.dto.learning.SystemLogResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/system-logs")
@Tag(name = "学习系统日志")
public class SystemLogController {

    private final AuthService authService;
    private final SystemLogService systemLogService;

    @GetMapping
    @Operation(summary = "系统日志列表")
    public List<SystemLogResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = LearningConstants.SystemLog.DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = authService.requireUser(authorization);
        return systemLogService.list(user.getId(), limit == null ? LearningConstants.SystemLog.DEFAULT_LIMIT : limit);
    }

    @PostMapping
    @Operation(summary = "写入系统日志")
    public SystemLogResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SystemLogRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return systemLogService.create(user.getId(), request == null ? new SystemLogRequest() : request);
    }

    @DeleteMapping
    @Operation(summary = "清空系统日志")
    public void clear(@RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        systemLogService.clear(user.getId());
    }
}
