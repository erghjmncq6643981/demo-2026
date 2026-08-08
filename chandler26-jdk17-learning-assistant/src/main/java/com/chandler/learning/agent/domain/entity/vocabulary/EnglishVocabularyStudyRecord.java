package com.chandler.learning.agent.domain.entity.vocabulary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
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

    /**
     * id 属性。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * term 属性。
     */
    private String term;

    /**
     * normalizedTerm 属性。
     */
    private String normalizedTerm;

    /**
     * agentCode 属性。
     */
    private String agentCode;

    /**
     * templateCode 属性。
     */
    private String templateCode;

    /**
     * provider 属性。
     */
    private String provider;

    /**
     * modelName 属性。
     */
    private String modelName;

    /**
     * sessionId 属性。
     */
    private Long sessionId;

    /**
     * rawContent 属性。
     */
    private String rawContent;

    /**
     * parsedJson 属性。
     */
    private String parsedJson;

    /**
     * tokenUsage 属性。
     */
    private Integer tokenUsage;

    /**
     * costTime 属性。
     */
    private Long costTime;

    /**
     * lookupCount 属性。
     */
    private Integer lookupCount;

    /**
     * lastLookupTime 属性。
     */
    private LocalDateTime lastLookupTime;
}
