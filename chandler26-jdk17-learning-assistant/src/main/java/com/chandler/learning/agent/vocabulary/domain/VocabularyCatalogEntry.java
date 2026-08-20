package com.chandler.learning.agent.vocabulary.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 词表版本词条，分别保留原词、系统建议词和人工确认词。
 */
@Data
@TableName("vocabulary_catalog_entry")
public class VocabularyCatalogEntry extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long catalogId;

    private Long catalogVersionId;

    private Integer sourceOrder;

    private String originalTerm;

    private String normalizedTerm;

    private String suggestedTerm;

    private String approvedTerm;

    private String phonetic;

    private String definitionText;

    private String warningCodes;

    private Boolean suspicious;

    private String reviewStatus;

    private Boolean published;

    /**
     * 返回审核后真正用于学习的词。
     */
    public String effectiveTerm() {
        if (approvedTerm != null && !approvedTerm.isBlank()) return approvedTerm.trim();
        if (originalTerm != null && !originalTerm.isBlank()) return originalTerm.trim();
        if (suggestedTerm != null && !suggestedTerm.isBlank()) return suggestedTerm.trim();
        if (normalizedTerm != null && !normalizedTerm.isBlank()) return normalizedTerm.trim();
        return id != null ? "词条#" + id : "未知词条";
    }
}
