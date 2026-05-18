package com.chandler.learning.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.PromptTemplateSaveRequest;
import com.chandler.learning.agent.domain.entity.AiPromptTemplate;
import com.chandler.learning.agent.mapper.AiPromptTemplateMapper;
import com.chandler.learning.agent.support.PromptRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 提示词模板服务。
 */
@Service
@RequiredArgsConstructor
public class AiPromptTemplateService {

    private final AiPromptTemplateMapper templateMapper;
    private final PromptRenderer promptRenderer;

    public AiPromptTemplate getByCode(String code) {
        return templateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getCode, code)
                .eq(AiPromptTemplate::getDeleted, false)
                .last("LIMIT 1"));
    }

    public List<AiPromptTemplate> list(String type) {
        return templateMapper.selectList(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(StringUtils.hasText(type), AiPromptTemplate::getType, type)
                .eq(AiPromptTemplate::getDeleted, false)
                .eq(AiPromptTemplate::getEnabled, true)
                .orderByAsc(AiPromptTemplate::getSequence));
    }

    public Long create(PromptTemplateSaveRequest request) {
        AiPromptTemplate existing = getByCode(request.getCode());
        if (existing != null) {
            throw new IllegalArgumentException("模板编码已存在: " + request.getCode());
        }
        AiPromptTemplate template = new AiPromptTemplate();
        copy(request, template);
        template.setEnabled(true);
        template.setDeleted(false);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(template);
        return template.getId();
    }

    public void update(Long id, PromptTemplateSaveRequest request) {
        AiPromptTemplate template = templateMapper.selectById(id);
        if (template == null || Boolean.TRUE.equals(template.getDeleted())) {
            throw new IllegalArgumentException("模板不存在: " + id);
        }
        AiPromptTemplate existing = getByCode(request.getCode());
        if (existing != null && !existing.getId().equals(id)) {
            throw new IllegalArgumentException("模板编码已存在: " + request.getCode());
        }
        copy(request, template);
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
    }

    public String render(String code, Map<String, Object> variables) {
        AiPromptTemplate template = getByCode(code);
        if (template == null) {
            throw new IllegalArgumentException("提示词模板不存在: " + code);
        }
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new IllegalArgumentException("提示词模板已禁用: " + code);
        }
        return promptRenderer.render(template.getContent(), variables);
    }

    private void copy(PromptTemplateSaveRequest request, AiPromptTemplate template) {
        template.setName(request.getName());
        template.setCode(request.getCode());
        template.setType(StringUtils.hasText(request.getType()) ? request.getType() : "user");
        template.setTags(request.getTags());
        template.setContent(request.getContent());
        template.setVariables(request.getVariables());
        template.setDescription(request.getDescription());
        template.setExampleInput(request.getExampleInput());
        template.setExampleOutput(request.getExampleOutput());
        template.setPublicTemplate(Boolean.TRUE.equals(request.getPublicTemplate()));
        template.setSequence(request.getSequence() == null ? 0 : request.getSequence());
    }
}
