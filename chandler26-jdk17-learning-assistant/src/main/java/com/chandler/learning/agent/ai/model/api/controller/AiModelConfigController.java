package com.chandler.learning.agent.ai.model.api.controller;

import com.chandler.learning.agent.ai.model.api.response.AiModelConfigResponse;
import com.chandler.learning.agent.ai.model.api.request.AiModelConfigSaveRequest;
import com.chandler.learning.agent.ai.model.api.response.AiModelOptionResponse;
import com.chandler.learning.agent.ai.model.api.response.AiModelConnectionTestResponse;
import com.chandler.learning.agent.ai.model.application.AiModelConnectionTestService;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
import com.chandler.learning.agent.common.constant.CommonConstants;
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
    private final AiModelConnectionTestService connectionTestService;
    private final CurrentUserContext currentUserContext;

    @GetMapping
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "模型配置列表")
    public List<AiModelConfigResponse> list(
            @RequestParam(defaultValue = "false") Boolean enabledOnly) {
        return modelConfigService.list(Boolean.TRUE.equals(enabledOnly));
    }

    /**
     * 查询学习界面可用模型，不包含管理信息。
     */
    @GetMapping("/available")
    @Operation(summary = "可用模型选项")
    public List<AiModelOptionResponse> available() {
        currentUserContext.requireUser();
        return modelConfigService.listAvailableOptions();
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    @PostMapping
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "创建模型配置")
    public AiModelConfigResponse create(
            @Valid @RequestBody AiModelConfigSaveRequest request) {
        return modelConfigService.create(request);
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    @PutMapping("/{id}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "更新模型配置")
    public AiModelConfigResponse update(
            @PathVariable Long id, @Valid @RequestBody AiModelConfigSaveRequest request) {
        return modelConfigService.update(id, request);
    }

    /**
     * 更新 {@code enable} 相关业务。
     */
    @PostMapping("/{id}/enable")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "启用模型配置")
    public void enable(
            @PathVariable Long id) {
        modelConfigService.updateEnabled(id, true);
    }

    /**
     * 更新 {@code disable} 相关业务。
     */
    @PostMapping("/{id}/disable")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "停用模型配置")
    public void disable(
            @PathVariable Long id) {
        modelConfigService.updateEnabled(id, false);
    }

    /**
     * 处理 {@code priority} 相关业务。
     */
    @PostMapping("/{id}/priority")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "更新模型配置优先级")
    public void priority(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer sequence = body.get("sequence") instanceof Number number ? number.intValue() : CommonConstants.DEFAULT_SEQUENCE;
        Boolean isDefault = body.get("isDefault") instanceof Boolean value && value;
        modelConfigService.updatePriority(id, sequence, isDefault);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{id}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "删除模型配置")
    public void delete(
            @PathVariable Long id) {
        modelConfigService.delete(id);
    }

    /**
     * 发送最小请求验证模型配置，不经过 Agent 或学习会话。
     */
    @PostMapping("/{id}/test")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "测试模型连接")
    public AiModelConnectionTestResponse test(
            @PathVariable Long id) {
        return connectionTestService.test(id);
    }
}
