package com.chandler.learning.agent.domain.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章学习记录响应。
 */
@Data
@Schema(name = "文章学习记录响应")
public class ArticleStudyResponse {

    @Schema(description = "文章学习记录 ID")
    private Long id;

    @Schema(description = "单词本 ID")
    private Long wordbookId;

    @Schema(description = "用于生成文章的词汇摘要")
    private List<ArticleStudyWordResponse> selectedWords;

    @Schema(description = "文章字数范围")
    private String wordCountRange;

    @Schema(description = "文章难度")
    private String difficulty;

    @Schema(description = "学习备注或生成要求")
    private String remark;

    @Schema(description = "是否命中缓存")
    private Boolean cacheHit;

    @Schema(description = "Agent 编码")
    private String agentCode;

    @Schema(description = "模板编码")
    private String templateCode;

    @Schema(description = "模型供应商")
    private String provider;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "AI 会话 ID")
    private Long sessionId;

    @Schema(description = "AI 原始回复")
    private String rawContent;

    @Schema(description = "解析后的结构化文章学习 JSON")
    private Object parsed;

    @Schema(description = "Token 使用量")
    private Integer tokenUsage;

    @Schema(description = "耗时，单位毫秒")
    private Long costTime;

    @Schema(description = "读取次数")
    private Integer lookupCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
