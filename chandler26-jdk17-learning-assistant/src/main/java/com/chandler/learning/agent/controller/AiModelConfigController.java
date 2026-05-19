package com.chandler.learning.agent.controller;

import com.chandler.learning.agent.domain.dto.AiModelConfigResponse;
import com.chandler.learning.agent.domain.dto.AiModelConfigSaveRequest;
import com.chandler.learning.agent.service.AiModelConfigService;
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

    @GetMapping
    @Operation(summary = "模型配置列表")
    public List<AiModelConfigResponse> list(@RequestParam(defaultValue = "false") Boolean enabledOnly) {
        return modelConfigService.list(Boolean.TRUE.equals(enabledOnly));
    }

    @PostMapping
    @Operation(summary = "创建模型配置")
    public AiModelConfigResponse create(@Valid @RequestBody AiModelConfigSaveRequest request) {
        return modelConfigService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模型配置")
    public AiModelConfigResponse update(@PathVariable Long id, @Valid @RequestBody AiModelConfigSaveRequest request) {
        return modelConfigService.update(id, request);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用模型配置")
    public void enable(@PathVariable Long id) {
        modelConfigService.updateEnabled(id, true);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用模型配置")
    public void disable(@PathVariable Long id) {
        modelConfigService.updateEnabled(id, false);
    }

    @PostMapping("/{id}/priority")
    @Operation(summary = "更新模型配置优先级")
    public void priority(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer sequence = body.get("sequence") instanceof Number number ? number.intValue() : LearningConstants.DEFAULT_SEQUENCE;
        Boolean isDefault = body.get("isDefault") instanceof Boolean value && value;
        modelConfigService.updatePriority(id, sequence, isDefault);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public void delete(@PathVariable Long id) {
        modelConfigService.delete(id);
    }
}
