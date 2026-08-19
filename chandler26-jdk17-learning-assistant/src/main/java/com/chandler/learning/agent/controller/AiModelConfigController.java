package com.chandler.learning.agent.controller;

import com.chandler.learning.agent.domain.dto.AiModelConfigResponse;
import com.chandler.learning.agent.domain.dto.AiModelConfigSaveRequest;
import com.chandler.learning.agent.domain.dto.AiModelOptionResponse;
import com.chandler.learning.agent.service.AiModelConfigService;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 模型配置控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/model-configs")
@Tag(name = "AI 模型配置")
public class AiModelConfigController {

    private final AiModelConfigService modelConfigService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "模型配置列表")
    public List<AiModelConfigResponse> list(@RequestParam(defaultValue = "false") Boolean enabledOnly) {
        authService.requireAdmin(null);
        return modelConfigService.list(Boolean.TRUE.equals(enabledOnly));
    }

    /**
     * 查询学习界面可用模型，不包含管理信息。
     */
    @GetMapping("/available")
    @Operation(summary = "可用模型选项")
    public List<AiModelOptionResponse> available() {
        authService.requireUser(null);
        return modelConfigService.listAvailableOptions();
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    @PostMapping
    @Operation(summary = "创建模型配置")
    public AiModelConfigResponse create(@Valid @RequestBody AiModelConfigSaveRequest request) {
        authService.requireAdmin(null);
        return modelConfigService.create(request);
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模型配置")
    public AiModelConfigResponse update(@PathVariable Long id, @Valid @RequestBody AiModelConfigSaveRequest request) {
        authService.requireAdmin(null);
        return modelConfigService.update(id, request);
    }

    /**
     * 更新 {@code enable} 相关业务。
     */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用模型配置")
    public void enable(@PathVariable Long id) {
        authService.requireAdmin(null);
        modelConfigService.updateEnabled(id, true);
    }

    /**
     * 更新 {@code disable} 相关业务。
     */
    @PostMapping("/{id}/disable")
    @Operation(summary = "停用模型配置")
    public void disable(@PathVariable Long id) {
        authService.requireAdmin(null);
        modelConfigService.updateEnabled(id, false);
    }

    /**
     * 处理 {@code priority} 相关业务。
     */
    @PostMapping("/{id}/priority")
    @Operation(summary = "更新模型配置优先级")
    public void priority(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(null);
        Integer sequence = body.get("sequence") instanceof Number number ? number.intValue() : LearningConstants.DEFAULT_SEQUENCE;
        Boolean isDefault = body.get("isDefault") instanceof Boolean value && value;
        modelConfigService.updatePriority(id, sequence, isDefault);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public void delete(@PathVariable Long id) {
        authService.requireAdmin(null);
        modelConfigService.delete(id);
    }
}
