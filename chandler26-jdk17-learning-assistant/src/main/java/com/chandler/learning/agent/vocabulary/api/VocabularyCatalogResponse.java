package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可供学习者创建计划的公共词本。
 */
@Data
public class VocabularyCatalogResponse {

    private Long catalogId;

    private Long catalogVersionId;

    private Long jobId;

    private String catalogName;

    private String sourceType;

    private String learningPurpose;

    private String status;

    private Integer totalCount;

    private LocalDateTime publishedTime;
}
