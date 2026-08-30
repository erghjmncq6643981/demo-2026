package com.chandler.learning.agent.vocabulary.domain.entity;

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

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 公共词本 ID。 */
    private Long catalogId;

    /** 公共词本版本 ID。 */
    private Long catalogVersionId;

    /** 词条在源文件中的序号。 */
    private Integer sourceOrder;

    /** 导入文件中的原始词汇。 */
    private String originalTerm;

    /** 归一化词汇。 */
    private String normalizedTerm;

    /** 系统建议修正后的词汇。 */
    private String suggestedTerm;

    /** 人工确认后的词汇。 */
    private String approvedTerm;

    /** 词汇音标。 */
    private String phonetic;

    /** 词汇释义文本。 */
    private String definitionText;

    /** 疑似问题编码列表。 */
    private String warningCodes;

    /** 是否疑似断词或异常词条。 */
    private Boolean suspicious;

    /** 词条审核状态。 */
    private String reviewStatus;

    /** 是否已发布。 */
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
