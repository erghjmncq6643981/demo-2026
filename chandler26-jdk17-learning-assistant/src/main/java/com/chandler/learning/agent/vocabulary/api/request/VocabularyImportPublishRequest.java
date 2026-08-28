package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 发布公共词本；保留可选个人词本参数以兼容旧客户端。
 */
@Data
public class VocabularyImportPublishRequest {

    @Schema(description = "关联业务标识")
    private Long wordbookId;
}
