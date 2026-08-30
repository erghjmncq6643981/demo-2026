package com.chandler.learning.agent.system.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.system.domain.constant.SystemLogConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 产品内系统日志类型。
 */
@Getter
public enum SystemLogType {

    SYSTEM(SystemLogConstants.TYPE_SYSTEM, "系统"),
    AUTH(SystemLogConstants.TYPE_AUTH, "账户"),
    AI(SystemLogConstants.TYPE_AI, "AI"),
    AI_MODEL(SystemLogConstants.TYPE_AI_MODEL, "AI 模型"),
    CACHE(SystemLogConstants.TYPE_CACHE, "缓存"),
    REVIEW(SystemLogConstants.TYPE_REVIEW, "复习"),
    WORDBOOK(SystemLogConstants.TYPE_WORDBOOK, "单词本"),
    AGENT(SystemLogConstants.TYPE_AGENT, "Agent"),
    PREFERENCE(SystemLogConstants.TYPE_PREFERENCE, "偏好设置"),
    VOCABULARY_IMPORT(SystemLogConstants.TYPE_VOCABULARY_IMPORT, "词表导入"),
    LEARNING_PLAN(SystemLogConstants.TYPE_LEARNING_PLAN, "学习计划"),
    ERROR(SystemLogConstants.TYPE_ERROR, "异常");

    private final String code;
    private final String label;

    SystemLogType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按编码解析对应的业务枚举。 */
    public static SystemLogType of(String code) {
        String normalized = StrUtil.blankToDefault(code, SYSTEM.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(SYSTEM);
    }
}
