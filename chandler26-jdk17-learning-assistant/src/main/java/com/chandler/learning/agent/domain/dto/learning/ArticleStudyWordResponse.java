package com.chandler.learning.agent.domain.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文章学习所选词汇摘要。
 */
@Data
@Schema(name = "文章学习词汇摘要")
public class ArticleStudyWordResponse {

    @Schema(description = "单词本词条 ID")
    private Long entryId;

    @Schema(description = "展示单词或短语")
    private String term;

    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    @Schema(description = "熟练状态")
    private String status;

    @Schema(description = "核心词性")
    private String partOfSpeech;

    @Schema(description = "核心中文含义")
    private String meaning;
}
