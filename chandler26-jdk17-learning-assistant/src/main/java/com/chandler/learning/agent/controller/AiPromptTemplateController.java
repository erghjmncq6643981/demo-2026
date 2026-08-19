package com.chandler.learning.agent.controller;

import com.chandler.learning.agent.domain.dto.PromptTemplateSaveRequest;
import com.chandler.learning.agent.domain.entity.AiPromptTemplate;
import com.chandler.learning.agent.service.AiPromptTemplateService;
import com.chandler.learning.agent.service.learning.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 提示词模板控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/prompt-templates")
@Tag(name = "AI 提示词模板")
public class AiPromptTemplateController {

    private final AiPromptTemplateService templateService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "模板列表")
    public List<AiPromptTemplate> list(@RequestParam(required = false) String type,
                                       @RequestParam(defaultValue = "true") Boolean enabledOnly) {
        return templateService.list(type, Boolean.TRUE.equals(enabledOnly));
    }

    /**
     * 查询 {@code detailByCode} 相关业务。
     */
    @GetMapping("/code/{code}")
    @Operation(summary = "根据编码查询模板")
    public AiPromptTemplate detailByCode(@PathVariable String code) {
        return templateService.getByCode(code);
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    @PostMapping
    @Operation(summary = "创建模板")
    public Long create(@Valid @RequestBody PromptTemplateSaveRequest request) {
        authService.requireAdmin(null);
        return templateService.create(request);
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模板")
    public void update(@PathVariable Long id, @Valid @RequestBody PromptTemplateSaveRequest request) {
        authService.requireAdmin(null);
        templateService.update(id, request);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板")
    public void delete(@PathVariable Long id) {
        authService.requireAdmin(null);
        templateService.delete(id);
    }

    /**
     * 更新 {@code clone} 相关业务。
     */
    @PostMapping("/{id}/clone")
    @Operation(summary = "复制模板")
    public Long clone(@PathVariable Long id) {
        authService.requireAdmin(null);
        return templateService.clone(id);
    }

    @PostMapping("/{code}/render")
    @Operation(summary = "渲染模板")
    public String render(@PathVariable String code, @RequestBody(required = false) Map<String, Object> variables) {
        return templateService.render(code, variables);
    }
}
