package com.chandler.learning.agent.reading.domain.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 知识反刍阅读后句子巩固模式。
 */
@Getter
public enum RuminationPracticeMode {

    COPY_SENTENCE("copy_sentence", "原句跟敲"),
    CLOZE_RECALL("cloze_recall", "目标词遮挡补全");

    private final String code;
    private final String label;

    RuminationPracticeMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 按请求编码解析巩固模式，未识别时返回 null。 */
    public static RuminationPracticeMode of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        return Arrays.stream(values())
                .filter(mode -> mode.code.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
    }
}
