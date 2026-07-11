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

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String term;

    private String normalizedTerm;

    private String agentCode;

    private String templateCode;

    private String provider;

    private String modelName;

    private Long sessionId;

    private String rawContent;

    private String parsedJson;

    private Integer tokenUsage;

    private Long costTime;

    private Integer lookupCount;

    private LocalDateTime lastLookupTime;
}
