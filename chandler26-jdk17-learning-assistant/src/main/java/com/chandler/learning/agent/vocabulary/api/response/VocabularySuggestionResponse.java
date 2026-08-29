package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 词汇搜索建议与自动补全响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "词汇搜索建议与自动补全响应")
public class VocabularySuggestionResponse {

    @Schema(description = "原始词汇拼写")
    private String term;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "核心词性")
    private String partOfSpeech;

    @Schema(description = "核心中文释义")
    private String meaning;

    @Schema(description = "历史查词热度/查询次数")
    private Integer lookupCount;
}
