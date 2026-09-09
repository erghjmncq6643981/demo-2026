package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/** 跨词本复用的词汇语义资产，身份为语言与标准词。 */
@Data
@TableName("vocabulary_semantic_asset")
public class VocabularySemanticAsset extends BaseEntity {
    /** 资产主键。 */
    @TableId
    private Long id;
    /** 词汇语言，当前英语业务为 en。 */
    private String language;
    /** 经小写、首尾空白处理的标准词。 */
    private String normalizedTerm;
    /** 去除词本身份信息的语义结果 JSON。 */
    private String semanticJson;
    /** 相关标准词数组，不保存源词本词条 ID。 */
    private String relatedTermsJson;
    /** 最近一次产生该结果的分析任务 ID。 */
    private Long sourceJobId;
}
