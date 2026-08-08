package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * AI 对话消息角色。
 * <p>
 * 约束模型接口支持的 system/user/assistant 三类角色，避免服务层散落角色字符串。
 */
@Getter
public enum ChatMessageRole {

    SYSTEM(LearningConstants.ChatSession.ROLE_SYSTEM, "系统"),
    USER(LearningConstants.ChatSession.ROLE_USER, "用户"),
    ASSISTANT(LearningConstants.ChatSession.ROLE_ASSISTANT, "助手");

    private final String code;
    private final String label;

    ChatMessageRole(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static ChatMessageRole of(String code) {
        String normalized = StrUtil.blankToDefault(code, USER.code).trim().toLowerCase();
        return from(normalized).orElse(USER);
    }

    /**
     * 处理 {@code from} 相关业务。
     */
    public static Optional<ChatMessageRole> from(String code) {
        String normalized = StrUtil.blankToDefault(code, "").trim().toLowerCase();
        return Arrays.stream(values())
                .filter(role -> role.code.equals(normalized))
                .findFirst();
    }

    /**
     * 判断 {@code conversational} 相关业务。
     */
    public static boolean conversational(String code) {
        return from(code)
                .filter(role -> role == USER || role == ASSISTANT)
                .isPresent();
    }
}
