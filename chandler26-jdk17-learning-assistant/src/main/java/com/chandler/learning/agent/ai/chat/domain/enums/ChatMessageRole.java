package com.chandler.learning.agent.ai.chat.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
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

    SYSTEM(AiChatConstants.ROLE_SYSTEM, "系统"),
    USER(AiChatConstants.ROLE_USER, "用户"),
    ASSISTANT(AiChatConstants.ROLE_ASSISTANT, "助手");

    private final String code;
    private final String label;

    ChatMessageRole(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按编码解析对应的业务枚举。 */
    public static ChatMessageRole of(String code) {
        String normalized = StrUtil.blankToDefault(code, USER.code).trim().toLowerCase();
        return from(normalized).orElse(USER);
    }

    /** 把外部角色值转换为内部消息角色。 */
    public static Optional<ChatMessageRole> from(String code) {
        String normalized = StrUtil.blankToDefault(code, "").trim().toLowerCase();
        return Arrays.stream(values())
                .filter(role -> role.code.equals(normalized))
                .findFirst();
    }

    /** 判断消息角色是否属于对话消息。 */
    public static boolean conversational(String code) {
        return from(code)
                .filter(role -> role == USER || role == ASSISTANT)
                .isPresent();
    }
}
