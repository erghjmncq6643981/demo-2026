package com.chandler.learning.agent.task.domain;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;

import java.util.Arrays;

/**
 * 可调度 AI 业务任务类型。
 */
public enum AiTaskType {

    SCENE_MATERIAL("scene_material", "场景材料生成"),
    SCENE_MATERIAL_REGENERATION("scene_material_regeneration", "场景材料重新生成"),
    SCENE_RELATED_VOCABULARY("scene_related_vocabulary", "场景相关词汇生成"),
    VOCABULARY_CARD("vocabulary_card", "批量词卡生成"),
    VOCABULARY_CATALOG_ANALYSIS("vocabulary_catalog_analysis", "词本关联分析"),
    ARTICLE_MATERIAL("article_material", "语境精读材料生成");

    private final String code;
    private final String title;

    AiTaskType(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    /** 使用稳定编码获取任务类型，拒绝调度未知 Worker。 */
    public static AiTaskType of(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.AI_ASYNC_TASK_TYPE_INVALID,
                        "不支持的 AI 任务类型: " + code));
    }
}
