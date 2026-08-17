package com.chandler.learning.agent.domain.entity.vocabulary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 词表导入版本。
 */
@Data
@TableName("vocabulary_catalog_version")
public class VocabularyCatalogVersion extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long catalogId;

    private Integer versionNo;

    private String status;

    private String sourceFormat;

    private String sourceFileName;

    private String sourceHash;

    private Integer totalCount;

    private Integer warningCount;

    private Integer reviewedWarningCount;

    private LocalDateTime publishedTime;
}
