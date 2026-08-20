package com.chandler.learning.agent.system.domain;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 产品内系统日志类型。
 */
@Getter
public enum SystemLogType {

    SYSTEM(LearningConstants.SystemLog.TYPE_SYSTEM, "系统"),
    AUTH(LearningConstants.SystemLog.TYPE_AUTH, "账户"),
    AI(LearningConstants.SystemLog.TYPE_AI, "AI"),
    AI_MODEL(LearningConstants.SystemLog.TYPE_AI_MODEL, "AI 模型"),
    CACHE(LearningConstants.SystemLog.TYPE_CACHE, "缓存"),
    REVIEW(LearningConstants.SystemLog.TYPE_REVIEW, "复习"),
    WORDBOOK(LearningConstants.SystemLog.TYPE_WORDBOOK, "单词本"),
    AGENT(LearningConstants.SystemLog.TYPE_AGENT, "Agent"),
    PREFERENCE(LearningConstants.SystemLog.TYPE_PREFERENCE, "偏好设置"),
    VOCABULARY_IMPORT(LearningConstants.SystemLog.TYPE_VOCABULARY_IMPORT, "词表导入"),
    LEARNING_PLAN(LearningConstants.SystemLog.TYPE_LEARNING_PLAN, "学习计划"),
    ERROR(LearningConstants.SystemLog.TYPE_ERROR, "异常");

    private final String code;
    private final String label;

    SystemLogType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static SystemLogType of(String code) {
        String normalized = StrUtil.blankToDefault(code, SYSTEM.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(SYSTEM);
    }
}
