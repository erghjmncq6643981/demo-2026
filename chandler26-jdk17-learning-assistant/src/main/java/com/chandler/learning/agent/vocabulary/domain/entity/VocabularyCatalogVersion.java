package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 词表导入版本。
 */
@Data
@TableName("vocabulary_catalog_version")
public class VocabularyCatalogVersion extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 公共词本 ID。 */
    private Long catalogId;

    /** 公共词本版本号。 */
    private Integer versionNo;

    /** 当前业务状态。 */
    private String status;

    /** 导入源文件格式。 */
    private String sourceFormat;

    /** 导入源文件名称。 */
    private String sourceFileName;

    /** 导入源文件内容哈希。 */
    private String sourceHash;

    /** 任务或分页数据总数。 */
    private Integer totalCount;

    /** 疑似问题数量。 */
    private Integer warningCount;

    /** 已确认问题数量。 */
    private Integer reviewedWarningCount;

    /** 发布时间。 */
    private LocalDateTime publishedTime;
}
