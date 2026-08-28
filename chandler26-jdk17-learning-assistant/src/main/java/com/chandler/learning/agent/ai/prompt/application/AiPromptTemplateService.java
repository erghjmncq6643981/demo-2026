package com.chandler.learning.agent.ai.prompt.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.prompt.api.request.PromptTemplateSaveRequest;
import com.chandler.learning.agent.ai.prompt.domain.entity.AiPromptTemplate;
import com.chandler.learning.agent.ai.prompt.domain.enums.PromptTemplateType;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.ai.prompt.infrastructure.mapper.AiPromptTemplateMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 提示词模板服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptTemplateService {

    private final AiPromptTemplateMapper templateMapper;
    private final PromptRenderer promptRenderer;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * 查询 {@code getByCode} 相关业务。
     */
    public AiPromptTemplate getByCode(String code) {
        return templateMapper.selectOne(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getCode, code)
                .eq(AiPromptTemplate::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /** 校验提示词模板存在且已启用，供其他业务域保存配置时调用。 */
    public AiPromptTemplate requireEnabled(String code) {
        AiPromptTemplate template = getByCode(code);
        if (template == null || !Boolean.TRUE.equals(template.getEnabled())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.PROMPT_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    /**
     * 查询 {@code list} 相关业务。
     */
    public List<AiPromptTemplate> list(String type) {
        return list(type, true);
    }

    /**
     * 查询 {@code list} 相关业务。
     */
    public List<AiPromptTemplate> list(String type, boolean enabledOnly) {
        String normalizedType = StringUtils.hasText(type) ? PromptTemplateType.of(type).getCode() : null;
        return templateMapper.selectList(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(StringUtils.hasText(normalizedType), AiPromptTemplate::getType, normalizedType)
                .eq(AiPromptTemplate::getDeleted, false)
                .eq(enabledOnly, AiPromptTemplate::getEnabled, true)
                .orderByAsc(AiPromptTemplate::getSequence));
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    public Long create(PromptTemplateSaveRequest request) {
        AiPromptTemplate existing = getByCode(request.getCode());
        if (existing != null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.PROMPT_TEMPLATE_CODE_EXISTS,
                    "模板编码已存在: " + request.getCode());
        }
        AiPromptTemplate template = new AiPromptTemplate();
        copy(request, template);
        template.setEnabled(true);
        template.setDeleted(false);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(template);
        systemLogService.record(null, SystemLogType.AGENT, "创建提示词模板", template.getName());
        log.info("用户「{}」创建了提示词模板「{}」", userDisplayNameService.currentUserName(), template.getName());
        return template.getId();
    }

    /**
     * 更新 {@code update} 相关业务。
     */
    public void update(Long id, PromptTemplateSaveRequest request) {
        AiPromptTemplate template = templateMapper.selectById(id);
        if (template == null || Boolean.TRUE.equals(template.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                    "模板不存在: " + id);
        }
        AiPromptTemplate existing = getByCode(request.getCode());
        if (existing != null && !existing.getId().equals(id)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.PROMPT_TEMPLATE_CODE_EXISTS,
                    "模板编码已存在: " + request.getCode());
        }
        copy(request, template);
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
        systemLogService.record(null, SystemLogType.AGENT, "更新提示词模板", template.getName());
        log.info("用户「{}」更新了提示词模板「{}」", userDisplayNameService.currentUserName(), template.getName());
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        long aliveCount = templateMapper.selectCount(new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getDeleted, false));
        if (aliveCount <= 1) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.PROMPT_TEMPLATE_LAST_NOT_DELETABLE,
                    "最后一个学习 Agent 模板不能删除");
        }
        AiPromptTemplate template = templateMapper.selectById(id);
        if (template == null || Boolean.TRUE.equals(template.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                    "模板不存在: " + id);
        }
        template.setDeleted(true);
        template.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(template);
        systemLogService.record(null, SystemLogType.AGENT, "删除提示词模板", template.getName());
        log.info("用户「{}」删除了提示词模板「{}」", userDisplayNameService.currentUserName(), template.getName());
    }

    /**
     * 更新 {@code clone} 相关业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long clone(Long id) {
        AiPromptTemplate source = templateMapper.selectById(id);
        if (source == null || Boolean.TRUE.equals(source.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                    "模板不存在: " + id);
        }
        String cloneCode = source.getCode() + "-" + LocalDateTime.now().getNano();
        if (getByCode(cloneCode) != null) {
            cloneCode = cloneCode + "-" + id;
        }
        AiPromptTemplate clone = new AiPromptTemplate();
        clone.setName(source.getName() + " 副本");
        clone.setCode(cloneCode);
        clone.setType(source.getType());
        clone.setTags(source.getTags());
        clone.setContent(source.getContent());
        clone.setVariables(source.getVariables());
        clone.setDescription(source.getDescription());
        clone.setExampleInput(source.getExampleInput());
        clone.setExampleOutput(source.getExampleOutput());
        clone.setPublicTemplate(source.getPublicTemplate());
        clone.setEnabled(source.getEnabled());
        clone.setSequence(source.getSequence());
        clone.setDeleted(false);
        clone.setCreateTime(LocalDateTime.now());
        clone.setUpdateTime(LocalDateTime.now());
        templateMapper.insert(clone);
        systemLogService.record(null, SystemLogType.AGENT, "复制提示词模板", source.getName());
        log.info("用户「{}」复制了提示词模板「{}」", userDisplayNameService.currentUserName(), source.getName());
        return clone.getId();
    }

    /**
     * 处理 {@code render} 相关业务。
     */
    public String render(String code, Map<String, Object> variables) {
        AiPromptTemplate template = getByCode(code);
        if (template == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.PROMPT_TEMPLATE_NOT_FOUND,
                    "提示词模板不存在: " + code);
        }
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.PROMPT_TEMPLATE_DISABLED,
                    "提示词模板已禁用: " + code);
        }
        return promptRenderer.render(template.getContent(), variables);
    }

    /**
     * 更新 {@code copy} 相关业务。
     */
    private void copy(PromptTemplateSaveRequest request, AiPromptTemplate template) {
        template.setName(request.getName());
        template.setCode(request.getCode());
        template.setType(PromptTemplateType.of(request.getType()).getCode());
        template.setTags(request.getTags());
        template.setContent(request.getContent());
        template.setVariables(request.getVariables());
        template.setDescription(request.getDescription());
        template.setExampleInput(request.getExampleInput());
        template.setExampleOutput(request.getExampleOutput());
        template.setPublicTemplate(Boolean.TRUE.equals(request.getPublicTemplate()));
        template.setSequence(request.getSequence() == null ? CommonConstants.DEFAULT_SEQUENCE : request.getSequence());
    }
}
