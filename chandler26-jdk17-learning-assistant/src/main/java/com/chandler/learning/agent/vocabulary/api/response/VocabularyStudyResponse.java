package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.chandler.learning.agent.vocabulary.api.response.VocabularyRelationResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyTagResponse;
import lombok.Data;

import java.util.List;

/**
 * 英语词汇学习响应。
 */
@Data
public class VocabularyStudyResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "英文词汇")
    private String term;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "是否命中缓存")
    private Boolean cacheHit;

    @Schema(description = "编码")
    private String agentCode;

    @Schema(description = "提示词模板编码")
    private String templateCode;

    @Schema(description = "AI 供应商")
    private String provider;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "关联业务标识")
    private Long sessionId;

    @Schema(description = "原始内容")
    private String rawContent;

    @Schema(description = "是否已解析")
    private Object parsed;

    @Schema(description = "Token 使用明细")
    private Integer tokenUsage;

    @Schema(description = "时间")
    private Long costTime;

    @Schema(description = "数量")
    private Integer lookupCount;

    @Schema(description = "标签列表")
    private List<VocabularyTagResponse> tags;

    @Schema(description = "词汇关联列表")
    private List<VocabularyRelationResponse> relations;
}
