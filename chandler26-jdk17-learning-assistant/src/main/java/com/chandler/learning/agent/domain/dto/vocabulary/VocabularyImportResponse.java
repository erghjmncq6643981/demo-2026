package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 词表导入任务及分页预览。
 */
@Data
public class VocabularyImportResponse {

    private Long jobId;

    private Long catalogId;

    private Long catalogVersionId;

    private String catalogName;

    private String learningPurpose;

    private String fileName;

    private String status;

    private Integer totalCount;

    private Integer warningCount;

    private Integer reviewedWarningCount;

    private Integer pendingWarningCount;

    private Integer page;

    private Integer pageSize;

    private Long filteredTotal;

    private List<VocabularyImportEntryResponse> items;

    private LocalDateTime createTime;
}
