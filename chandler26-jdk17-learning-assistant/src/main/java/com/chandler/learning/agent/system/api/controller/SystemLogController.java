package com.chandler.learning.agent.system.api.controller;

import com.chandler.learning.agent.system.api.response.SystemLogResponse;
import com.chandler.learning.agent.system.api.request.SystemLogRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.constant.SystemLogConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SystemLogController 类。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/system-logs")
@Tag(name = "学习系统日志")
public class SystemLogController {

    private final CurrentUserContext currentUserContext;
    private final SystemLogService systemLogService;

    /**
     * 查询 {@code list} 相关业务。
     */
    @GetMapping
    @Operation(summary = "系统日志列表")
    public List<SystemLogResponse> list(
            @RequestParam(defaultValue = SystemLogConstants.DEFAULT_LIMIT_PARAM) Integer limit) {
        LearningUser user = currentUserContext.requireUser();
        return systemLogService.list(user.getId(), limit == null ? SystemLogConstants.DEFAULT_LIMIT : limit);
    }

    /** 接收前端产品交互日志并异步写入；日志排队不阻断当前用户操作。 */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "写入系统日志")
    public void create(
            @Valid @RequestBody SystemLogRequest request) {
        LearningUser user = currentUserContext.requireUser();
        systemLogService.recordClient(user.getId(), request.getType(), request.getTitle(), request.getDetail(),
                request.getBusinessType(), request.getBusinessId());
    }

    @DeleteMapping
    @Operation(summary = "清空系统日志")
    public void clear() {
        LearningUser user = currentUserContext.requireUser();
        systemLogService.clear(user.getId());
    }
}
