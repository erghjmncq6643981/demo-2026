package com.chandler.learning.agent.ai.agent.api.controller;

import com.chandler.learning.agent.ai.agent.api.request.AgentSaveRequest;
import com.chandler.learning.agent.ai.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.ai.agent.application.AiAgentService;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI Agent 控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/agents")
@Tag(name = "AI Agent")
public class AiAgentController {

    private final AiAgentService agentService;

    @GetMapping
    @Operation(summary = "Agent 列表")
    public List<AiAgent> list(@RequestParam(required = false) String type,
                              @RequestParam(defaultValue = "true") Boolean enabledOnly) {
        return agentService.list(type, Boolean.TRUE.equals(enabledOnly));
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    @GetMapping("/{id}")
    @Operation(summary = "Agent 详情")
    public AiAgent detail(@PathVariable Long id) {
        return agentService.getById(id);
    }

    /**
     * 查询 {@code detailByCode} 相关业务。
     */
    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码查询 Agent")
    public AiAgent detailByCode(@PathVariable String code) {
        return agentService.getByCode(code);
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    @PostMapping
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "创建 Agent")
    public Long create(
            @Valid @RequestBody AgentSaveRequest request) {
        return agentService.create(request);
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    @PutMapping("/{id}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "更新 Agent")
    public void update(
            @PathVariable Long id, @Valid @RequestBody AgentSaveRequest request) {
        agentService.update(id, request);
    }

    /**
     * 更新 {@code enable} 相关业务。
     */
    @PostMapping("/{id}/enable")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "启用 Agent")
    public void enable(
            @PathVariable Long id) {
        agentService.updateEnabled(id, true);
    }

    /**
     * 更新 {@code disable} 相关业务。
     */
    @PostMapping("/{id}/disable")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "停用 Agent")
    public void disable(
            @PathVariable Long id) {
        agentService.updateEnabled(id, false);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "删除 Agent")
    public void delete(
            @PathVariable Long id) {
        agentService.delete(id);
    }

    /**
     * 更新 {@code clone} 相关业务。
     */
    @PostMapping("/{id}/clone")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "复制 Agent")
    public Long clone(
            @PathVariable Long id) {
        return agentService.clone(id);
    }
}
