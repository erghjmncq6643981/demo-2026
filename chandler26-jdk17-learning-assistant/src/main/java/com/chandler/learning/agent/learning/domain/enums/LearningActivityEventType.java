package com.chandler.learning.agent.learning.domain.enums;

import java.util.Arrays;

/** 学习活动事件类型及其日汇总指标编码。 */
public enum LearningActivityEventType {

    WORD_ADDED("word_added", "加入单词本"),
    WORD_LEARNED("word_learned", "完成词汇学习"),
    WORD_REVIEWED("word_reviewed", "完成词汇检查"),
    SCENE_STARTED("scene_started", "开始场景学习"),
    SCENE_COMPLETED("scene_completed", "完成场景学习"),
    ARTICLE_STARTED("article_started", "开始语境精读"),
    ARTICLE_COMPLETED("article_completed", "完成语境精读"),
    RUMINATION_SENTENCE_PRACTICED("rumination_sentence_practiced", "完成反刍句型练习"),
    RUMINATION_PROBE_SUBMITTED("rumination_probe_submitted", "提交反刍微探针"),
    RUMINATION_MATERIAL_GENERATED("rumination_material_generated", "生成反刍场景材料"),
    AUDIO_PLAYED("audio_played", "有效播放音频");

    private final String code;
    private final String label;

    LearningActivityEventType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 判断是否为知识反刍维度的有效学习活动。 */
    public boolean isRuminationActivity() {
        return this == RUMINATION_SENTENCE_PRACTICED
                || this == RUMINATION_PROBE_SUBMITTED
                || this == RUMINATION_MATERIAL_GENERATED;
    }

    /** 按编码判断是否为知识反刍维度的指标。 */
    public static boolean isRuminationMetric(String code) {
        LearningActivityEventType type = of(code);
        return type != null && type.isRuminationActivity();
    }

    /** 按编码解析事件类型，未知编码返回 null。 */
    public static LearningActivityEventType of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
