package com.chandler.learning.agent.reading.domain.enums;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文章学习字数范围枚举。
 * <p>
 * 字数以固定跨度选择，既便于产品交互，也便于作为文章缓存的一部分稳定命中。
 */
@Getter
public enum ArticleWordCountRange {

    SHORT("150-200", 150, 200, "150-200 词"),
    MEDIUM("300-500", 300, 500, "300-500 词"),
    LONG("500-700", 500, 700, "500-700 词"),
    EXTRA_LONG("800-1000", 800, 1000, "800-1000 词");

    private final String code;
    private final int minWords;
    private final int maxWords;
    private final String label;

    ArticleWordCountRange(String code, int minWords, int maxWords, String label) {
        this.code = code;
        this.minWords = minWords;
        this.maxWords = maxWords;
        this.label = label;
    }

    /** 按编码解析对应的业务枚举。 */
    public static ArticleWordCountRange of(String code) {
        String normalized = StrUtil.blankToDefault(code, MEDIUM.code).trim();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> LearningAssistantException.badRequest(
                        LearningErrorCode.ARTICLE_WORDS_INVALID,
                        "文章字数范围不支持: " + code));
    }
}
