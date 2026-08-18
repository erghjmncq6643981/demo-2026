package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * AI 调用场景枚举。
 * <p>
 * 与 {@link LearningScene} 的会话复用边界不同，本枚举描述一次模型调用要完成的具体任务，
 * 同时声明响应是否必须为 JSON 以及必须存在的根字段，便于按场景追踪和验收模型响应。
 */
@Getter
public enum AiInvocationScene {

    GENERAL_CHAT("general_chat", "通用 Agent 对话", false, List.of()),
    VOCABULARY_FOLLOW_UP("vocabulary_follow_up", "词汇学习追问", false, List.of()),
    VOCABULARY_CARD_SINGLE("vocabulary_card_single", "单词词卡生成", true,
            List.of("term", "definitions", "examples", "collocations", "memory_tips")),
    VOCABULARY_CARD_BATCH("vocabulary_card_batch", "批量词卡生成", true, List.of("cards")),
    VOCABULARY_CATALOG_ANALYSIS("vocabulary_catalog_analysis", "公共词本关联分析", true,
            List.of("entries")),
    ARTICLE_STUDY_MATERIAL("article_study_material", "语境精读材料生成", true,
            List.of("title", "article", "translation", "vocabulary_focus", "grammar_points", "practice")),
    VOCABULARY_SCENE_UNIT("vocabulary_scene_unit", "词汇大挑战场景单元生成", true,
            List.of("title", "learning_text", "translation", "vocabulary"));

    @JsonValue
    private final String code;
    private final String title;
    private final boolean structuredResponse;
    private final List<String> requiredRootFields;

    AiInvocationScene(String code, String title, boolean structuredResponse, List<String> requiredRootFields) {
        this.code = code;
        this.title = title;
        this.structuredResponse = structuredResponse;
        this.requiredRootFields = requiredRootFields;
    }

    /**
     * 使用稳定业务编码反序列化，未传时兼容为通用对话，非法值直接返回业务错误。
     */
    @JsonCreator
    public static AiInvocationScene of(String code) {
        if (StrUtil.isBlank(code)) {
            return GENERAL_CHAT;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(scene -> scene.code.equals(normalized)
                        || scene.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.AI_INVOCATION_SCENE_INVALID,
                        "不支持的 AI 调用场景: " + code));
    }
}
