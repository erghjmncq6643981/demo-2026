package com.chandler.learning.agent.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.Getter;

import java.util.Arrays;

/**
 * 英语发音口音类型。
 */
@Getter
public enum SpeechVoiceType {

    US(LearningConstants.UserPreference.VOICE_TYPE_US, "美音"),
    UK(LearningConstants.UserPreference.VOICE_TYPE_UK, "英音");

    private final String code;
    private final String label;

    SpeechVoiceType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 处理 {@code of} 相关业务。
     */
    public static SpeechVoiceType of(String code) {
        String normalized = StrUtil.blankToDefault(code, US.code).trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(US);
    }
}
