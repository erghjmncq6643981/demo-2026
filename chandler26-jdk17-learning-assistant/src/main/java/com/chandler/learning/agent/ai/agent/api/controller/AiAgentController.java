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
    /** Agent 列表。 */

    @GetMapping
    @Operation(summary = "Agent 列表")
    public List<AiAgent> list(@RequestParam(required = false) String type,
                              @RequestParam(defaultValue = "true") Boolean enabledOnly) {
        return agentService.list(type, Boolean.TRUE.equals(enabledOnly));
    }

    /** Agent 详情。 */
    @GetMapping("/{id}")
    @Operation(summary = "Agent 详情")
    public AiAgent detail(@PathVariable Long id) {
        return agentService.getById(id);
    }

    /** 根据编码查询 Agent。 */
    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码查询 Agent")
    public AiAgent detailByCode(@PathVariable String code) {
        return agentService.getByCode(code);
    }

    /** 创建 Agent。 */
    @PostMapping
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "创建 Agent")
    public Long create(
            @Valid @RequestBody AgentSaveRequest request) {
        return agentService.create(request);
    }

    /** 更新 Agent。 */
    @PutMapping("/{id}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "更新 Agent")
    public void update(
            @PathVariable Long id, @Valid @RequestBody AgentSaveRequest request) {
        agentService.update(id, request);
    }

    /** 启用 Agent。 */
    @PostMapping("/{id}/enable")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "启用 Agent")
    public void enable(
            @PathVariable Long id) {
        agentService.updateEnabled(id, true);
    }

    /** 停用 Agent。 */
    @PostMapping("/{id}/disable")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "停用 Agent")
    public void disable(
            @PathVariable Long id) {
        agentService.updateEnabled(id, false);
    }

    /** 删除 Agent。 */
    @DeleteMapping("/{id}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "删除 Agent")
    public void delete(
            @PathVariable Long id) {
        agentService.delete(id);
    }

    /** 复制 Agent。 */
    @PostMapping("/{id}/clone")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "复制 Agent")
    public Long clone(
            @PathVariable Long id) {
        return agentService.clone(id);
    }
}
