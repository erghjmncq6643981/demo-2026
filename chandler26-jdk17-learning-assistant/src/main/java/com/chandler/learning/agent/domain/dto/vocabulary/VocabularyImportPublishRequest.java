package com.chandler.learning.agent.domain.dto.vocabulary;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发布词表并导入个人单词本请求。
 */
@Data
public class VocabularyImportPublishRequest {

    @NotNull(message = "目标单词本不能为空")
    private Long wordbookId;
}
