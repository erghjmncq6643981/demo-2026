package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

/**
 * 发布公共词本；保留可选个人词本参数以兼容旧客户端。
 */
@Data
public class VocabularyImportPublishRequest {

    private Long wordbookId;
}
