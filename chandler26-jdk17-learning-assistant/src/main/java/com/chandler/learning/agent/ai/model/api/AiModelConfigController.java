package com.chandler.learning.agent.ai.model.api;

import com.chandler.learning.agent.ai.model.api.AiModelConfigResponse;
import com.chandler.learning.agent.ai.model.api.AiModelConfigSaveRequest;
import com.chandler.learning.agent.ai.model.api.AiModelOptionResponse;
import com.chandler.learning.agent.ai.model.api.AiModelConnectionTestResponse;
import com.chandler.learning.agent.ai.model.application.AiModelConnectionTestService;
import com.chandler.learning.agent.ai.model.application.AiModelConfigService;
import com.chandler.learning.agent.identity.application.AuthService;
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
    private final AiModelConnectionTestService connectionTestService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "模型配置列表")
    public List<AiModelConfigResponse> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam(defaultValue = "false") Boolean enabledOnly) {
        authService.requireAdmin(authorization);
        return modelConfigService.list(Boolean.TRUE.equals(enabledOnly));
    }

    /**
     * 查询学习界面可用模型，不包含管理信息。
     */
    @GetMapping("/available")
    @Operation(summary = "可用模型选项")
    public List<AiModelOptionResponse> available(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return modelConfigService.listAvailableOptions();
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    @PostMapping
    @Operation(summary = "创建模型配置")
    public AiModelConfigResponse create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @Valid @RequestBody AiModelConfigSaveRequest request) {
        authService.requireAdmin(authorization);
        return modelConfigService.create(request);
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模型配置")
    public AiModelConfigResponse update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id, @Valid @RequestBody AiModelConfigSaveRequest request) {
        authService.requireAdmin(authorization);
        return modelConfigService.update(id, request);
    }

    /**
     * 更新 {@code enable} 相关业务。
     */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用模型配置")
    public void enable(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        authService.requireAdmin(authorization);
        modelConfigService.updateEnabled(id, true);
    }

    /**
     * 更新 {@code disable} 相关业务。
     */
    @PostMapping("/{id}/disable")
    @Operation(summary = "停用模型配置")
    public void disable(@RequestHeader(value = "Authorization", required = false) String authorization,
                        @PathVariable Long id) {
        authService.requireAdmin(authorization);
        modelConfigService.updateEnabled(id, false);
    }

    /**
     * 处理 {@code priority} 相关业务。
     */
    @PostMapping("/{id}/priority")
    @Operation(summary = "更新模型配置优先级")
    public void priority(@RequestHeader(value = "Authorization", required = false) String authorization,
                         @PathVariable Long id, @RequestBody Map<String, Object> body) {
        authService.requireAdmin(authorization);
        Integer sequence = body.get("sequence") instanceof Number number ? number.intValue() : LearningConstants.DEFAULT_SEQUENCE;
        Boolean isDefault = body.get("isDefault") instanceof Boolean value && value;
        modelConfigService.updatePriority(id, sequence, isDefault);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模型配置")
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable Long id) {
        authService.requireAdmin(authorization);
        modelConfigService.delete(id);
    }

    /**
     * 发送最小请求验证模型配置，不经过 Agent 或学习会话。
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "测试模型连接")
    public AiModelConnectionTestResponse test(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) {
        authService.requireAdmin(authorization);
        return connectionTestService.test(id);
    }
}
