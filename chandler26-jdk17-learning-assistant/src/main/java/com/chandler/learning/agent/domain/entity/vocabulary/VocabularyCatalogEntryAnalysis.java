package com.chandler.learning.agent.domain.entity.vocabulary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

/** 词本词条的语义索引结果，供场景候选统筹复用。 */
@Data
@TableName("vocabulary_catalog_entry_analysis")
public class VocabularyCatalogEntryAnalysis extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    /** 分析结果主键。 */
    private Long id;

    /** 产生结果的分析任务 ID。 */
    private Long jobId;

    /** 公共词本 ID。 */
    private Long catalogId;

    /** 公共词本版本 ID。 */
    private Long catalogVersionId;

    /** 被分析的词条 ID。 */
    private Long catalogEntryId;

    /** 稳定的主语义分组编码。 */
    private String primaryGroupCode;

    /** 主语义分组名称。 */
    private String primaryGroupName;

    /** 领域编码。 */
    private String domainCode;

    /** 子主题编码。 */
    private String subTopicCode;

    /** 语义标签 JSON 数组。 */
    private String tagsJson;

    /** 同义、反义、词族或主题相关词条 ID JSON 数组。 */
    private String relatedEntryIdsJson;

    /** AI 建议难度。 */
    private String difficultyLevel;

    /** 分析置信度。 */
    private Double confidence;

    /** 分析结果状态。 */
    private String status;

    /** 结果来源。 */
    private String source;

    /** 词本版本内分析修订号。 */
    private Integer analysisVersion;

    /** 受控长度的原始分析 JSON。 */
    private String rawResultJson;
}
