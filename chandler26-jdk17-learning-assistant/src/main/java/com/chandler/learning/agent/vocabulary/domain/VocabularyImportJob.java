package com.chandler.learning.agent.vocabulary.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 词表导入任务。
 */
@Data
@TableName("vocabulary_import_job")
public class VocabularyImportJob extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long catalogId;

    private Long catalogVersionId;

    private String sourceFormat;

    private String sourceFileName;

    private String status;

    private Integer totalCount;

    private Integer warningCount;

    private Integer reviewedWarningCount;

    private String errorMessage;

    private LocalDateTime finishedTime;
}
