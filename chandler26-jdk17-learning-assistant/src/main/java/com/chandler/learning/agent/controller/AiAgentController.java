package com.chandler.learning.agent.controller;

import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.AgentSaveRequest;
import com.chandler.learning.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.service.AiAgentService;
import com.chandler.learning.agent.service.AiChatService;
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
    private final AiChatService chatService;

    @PostMapping("/chat")
    @Operation(summary = "Agent 同步对话")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return chatService.chat(request);
    }

    @GetMapping
    @Operation(summary = "Agent 列表")
    public List<AiAgent> list(@RequestParam(required = false) String type,
                              @RequestParam(defaultValue = "true") Boolean enabledOnly) {
        return agentService.list(type, Boolean.TRUE.equals(enabledOnly));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Agent 详情")
    public AiAgent detail(@PathVariable Long id) {
        return agentService.getById(id);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码查询 Agent")
    public AiAgent detailByCode(@PathVariable String code) {
        return agentService.getByCode(code);
    }

    @PostMapping
    @Operation(summary = "创建 Agent")
    public Long create(@Valid @RequestBody AgentSaveRequest request) {
        return agentService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 Agent")
    public void update(@PathVariable Long id, @Valid @RequestBody AgentSaveRequest request) {
        agentService.update(id, request);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用 Agent")
    public void enable(@PathVariable Long id) {
        agentService.updateEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用 Agent")
    public void disable(@PathVariable Long id) {
        agentService.updateEnabled(id, false);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 Agent")
    public void delete(@PathVariable Long id) {
        agentService.delete(id);
    }

    @PostMapping("/{id}/clone")
    @Operation(summary = "复制 Agent")
    public Long clone(@PathVariable Long id) {
        return agentService.clone(id);
    }
}
