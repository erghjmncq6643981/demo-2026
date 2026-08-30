package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 英语词汇 AI 学习结果缓存。
 */
@Data
@TableName("english_vocabulary_study_record")
@Schema(name = "英语词汇学习记录")
public class EnglishVocabularyStudyRecord extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 英文词汇或短语。 */
    private String term;

    /** 归一化词汇。 */
    private String normalizedTerm;

    /** Agent 编码。 */
    private String agentCode;

    /** 提示词模板编码。 */
    private String templateCode;

    /** 模型供应商。 */
    private String provider;

    /** 模型名称。 */
    private String modelName;

    /** AI 会话 ID。 */
    private Long sessionId;

    /** AI 原始响应内容。 */
    private String rawContent;

    /** 解析后的结构化 JSON。 */
    private String parsedJson;

    /** 模型调用 Token 总数。 */
    private Integer tokenUsage;

    /** 处理耗时，单位毫秒。 */
    private Long costTime;

    /** 累计查询次数。 */
    private Integer lookupCount;

    /** 最近查词时间。 */
    private LocalDateTime lastLookupTime;
}
