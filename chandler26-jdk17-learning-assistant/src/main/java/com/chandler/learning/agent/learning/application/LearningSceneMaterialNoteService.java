package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.learning.api.SceneMaterialNoteRequest;
import com.chandler.learning.agent.learning.api.SceneMaterialNoteResponse;
import com.chandler.learning.agent.learning.domain.LearningPlan;
import com.chandler.learning.agent.learning.domain.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.LearningSceneMaterial;
import com.chandler.learning.agent.learning.domain.LearningSceneMaterialNote;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.infrastructure.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningSceneMaterialNoteMapper;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 场景材料笔记服务，笔记只属于当前用户的指定学习计划场景。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningSceneMaterialNoteService {

    private static final String MARKDOWN = "markdown";

    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningSceneMaterialMapper materialMapper;
    private final LearningSceneMaterialNoteMapper noteMapper;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /** 查询场景材料笔记；没有保存过时返回空 Markdown 文档。 */
    public SceneMaterialNoteResponse get(Long userId, Long planId, Long unitId) {
        LearningSceneMaterial material = requireMaterial(userId, planId, unitId);
        LearningSceneMaterialNote note = findNote(userId, planId, unitId, material.getId());
        return toResponse(note, planId, unitId, material.getId());
    }

    /** 保存或更新场景材料 Markdown 笔记。 */
    @Transactional(rollbackFor = Exception.class)
    public SceneMaterialNoteResponse save(Long userId, Long planId, Long unitId, SceneMaterialNoteRequest request) {
        LearningSceneMaterial material = requireMaterial(userId, planId, unitId);
        String content = request == null || request.getContent() == null ? "" : request.getContent();
        LearningSceneMaterialNote note = findNote(userId, planId, unitId, material.getId());
        LocalDateTime now = LocalDateTime.now();
        if (note == null) {
            note = new LearningSceneMaterialNote();
            note.setUserId(userId);
            note.setPlanId(planId);
            note.setUnitId(unitId);
            note.setSceneMaterialId(material.getId());
            note.setCreateBy(userId);
            note.setCreateTime(now);
            note.setDeleted(false);
            note.setVersion(LearningConstants.ZERO);
        }
        note.setContent(content);
        note.setContentFormat(MARKDOWN);
        note.setUpdateBy(userId);
        note.setUpdateTime(now);
        if (note.getId() == null) {
            noteMapper.insert(note);
        } else {
            noteMapper.updateById(note);
        }
        systemLogService.record(userId, SystemLogType.LEARNING_PLAN,
                "保存场景材料笔记", "场景「" + material.getTitle() + "」");
        log.info("用户「{}」保存场景材料「{}」的 Markdown 笔记，字符数 {}",
                userDisplayNameService.userName(userId), material.getTitle(), content.length());
        return toResponse(note, planId, unitId, material.getId());
    }

    private LearningSceneMaterial requireMaterial(Long userId, Long planId, Long unitId) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (plan == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.LEARNING_PLAN_NOT_FOUND);
        }
        LearningPlanUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getId, unitId)
                .eq(LearningPlanUnit::getPlanId, planId)
                .eq(LearningPlanUnit::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (unit == null || unit.getSceneMaterialId() == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND);
        }
        LearningSceneMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<LearningSceneMaterial>()
                .eq(LearningSceneMaterial::getId, unit.getSceneMaterialId())
                .eq(LearningSceneMaterial::getUnitId, unitId)
                .eq(LearningSceneMaterial::getPlanId, planId)
                .eq(LearningSceneMaterial::getUserId, userId)
                .eq(LearningSceneMaterial::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (material == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND);
        }
        return material;
    }

    private LearningSceneMaterialNote findNote(Long userId, Long planId, Long unitId, Long materialId) {
        return noteMapper.selectOne(new LambdaQueryWrapper<LearningSceneMaterialNote>()
                .eq(LearningSceneMaterialNote::getUserId, userId)
                .eq(LearningSceneMaterialNote::getPlanId, planId)
                .eq(LearningSceneMaterialNote::getUnitId, unitId)
                .eq(LearningSceneMaterialNote::getSceneMaterialId, materialId)
                .eq(LearningSceneMaterialNote::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private SceneMaterialNoteResponse toResponse(LearningSceneMaterialNote note,
                                                 Long planId, Long unitId, Long materialId) {
        SceneMaterialNoteResponse response = new SceneMaterialNoteResponse();
        response.setId(note == null ? null : note.getId());
        response.setPlanId(planId);
        response.setUnitId(unitId);
        response.setSceneMaterialId(materialId);
        response.setContent(note == null ? "" : note.getContent());
        response.setContentFormat(note == null || !StringUtils.hasText(note.getContentFormat())
                ? MARKDOWN : note.getContentFormat());
        response.setUpdateTime(note == null ? null : note.getUpdateTime());
        return response;
    }
}
