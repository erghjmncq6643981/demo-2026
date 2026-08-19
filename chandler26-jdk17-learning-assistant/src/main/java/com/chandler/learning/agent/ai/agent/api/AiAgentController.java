package com.chandler.learning.agent.ai.agent.api;

import com.chandler.learning.agent.ai.agent.api.AgentSaveRequest;
import com.chandler.learning.agent.ai.agent.domain.AiAgent;
import com.chandler.learning.agent.ai.agent.application.AiAgentService;
import com.chandler.learning.agent.service.learning.AuthService;
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
    private final AuthService authService;

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
    @Operation(summary = "创建 Agent")
    public Long create(@Valid @RequestBody AgentSaveRequest request) {
        authService.requireAdmin(null);
        return agentService.create(request);
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent")
    public void update(@PathVariable Long id, @Valid @RequestBody AgentSaveRequest request) {
        authService.requireAdmin(null);
        agentService.update(id, request);
    }

    /**
     * 更新 {@code enable} 相关业务。
     */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用 Agent")
    public void enable(@PathVariable Long id) {
        authService.requireAdmin(null);
        agentService.updateEnabled(id, true);
    }

    /**
     * 更新 {@code disable} 相关业务。
     */
    @PostMapping("/{id}/disable")
    @Operation(summary = "停用 Agent")
    public void disable(@PathVariable Long id) {
        authService.requireAdmin(null);
        agentService.updateEnabled(id, false);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Agent")
    public void delete(@PathVariable Long id) {
        authService.requireAdmin(null);
        agentService.delete(id);
    }

    /**
     * 更新 {@code clone} 相关业务。
     */
    @PostMapping("/{id}/clone")
    @Operation(summary = "复制 Agent")
    public Long clone(@PathVariable Long id) {
        authService.requireAdmin(null);
        return agentService.clone(id);
    }
}
