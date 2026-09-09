package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 词汇公共词本数据标签响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyDataTagResponse {

    @Schema(description = "标签编码，如 self_study, cet4, cet6, ielts")
    private String code;

    @Schema(description = "标签显示名称，如 自考, 四级, 六级, 雅思")
    private String label;
}
