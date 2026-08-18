package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 学习场景枚举。
 * <p>
 * 一个用户在同一学习场景内复用一个 AI 会话，不同场景彼此隔离。
 */
@Getter
public enum LearningScene {

    ENGLISH_VOCABULARY(LearningConstants.ChatSession.SCENE_ENGLISH_VOCABULARY, "英语词汇学习"),
    ENGLISH_ARTICLE(LearningConstants.ChatSession.SCENE_ENGLISH_ARTICLE, "英语语境精读"),
    ENGLISH_VOCABULARY_PLAN(LearningConstants.ChatSession.SCENE_ENGLISH_VOCABULARY_PLAN, "英语场景词汇计划"),
    MATH(LearningConstants.ChatSession.SCENE_MATH, "数学学习"),
    PINYIN(LearningConstants.ChatSession.SCENE_PINYIN, "汉语拼音学习"),
    WRITING(LearningConstants.ChatSession.SCENE_WRITING, "写作学习");

    private final String code;
    private final String title;

    LearningScene(String code, String title) {
        this.code = code;
        this.title = title;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static LearningScene of(String code) {
        String normalized = StrUtil.blankToDefault(code, ENGLISH_VOCABULARY.code).trim();
        return Arrays.stream(values())
                .filter(scene -> scene.code.equals(normalized))
                .findFirst()
                .orElse(ENGLISH_VOCABULARY);
    }

    /**
     * 处理 {@code titleOf} 相关业务。
     */
    public static String titleOf(String code) {
        return of(code).title;
    }
}
