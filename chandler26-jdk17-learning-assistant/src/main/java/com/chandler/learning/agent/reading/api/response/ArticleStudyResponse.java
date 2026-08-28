package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 语境精读记录响应。
 */
@Data
@Schema(name = "语境精读记录响应")
public class ArticleStudyResponse {

    @Schema(description = "语境精读记录 ID")
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

    @Schema(description = "解析后的结构化语境精读 JSON")
    private Object parsed;

    @Schema(description = "Token 使用量")
    private Integer tokenUsage;

    @Schema(description = "耗时，单位毫秒")
    private Long costTime;

    @Schema(description = "读取次数")
    private Integer lookupCount;

    @Schema(description = "学习状态：generated、in_progress、completed")
    private String studyStatus;

    @Schema(description = "当前阶段：reading、vocabulary、check、completed")
    private String currentStage;

    @Schema(description = "检测题总数")
    private Integer practiceTotal;

    @Schema(description = "检测答对数")
    private Integer practiceCorrect;

    @Schema(description = "检测得分，0-100")
    private Integer practiceScore;

    @Schema(description = "开始学习时间")
    private LocalDateTime startedTime;

    @Schema(description = "完成学习时间")
    private LocalDateTime completedTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
