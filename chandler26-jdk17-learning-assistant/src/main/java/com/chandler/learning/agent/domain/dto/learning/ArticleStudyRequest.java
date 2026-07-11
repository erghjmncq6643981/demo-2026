package com.chandler.learning.agent.domain.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 文章学习生成请求。
 */
@Data
@Schema(name = "文章学习生成请求")
public class ArticleStudyRequest {

    @NotNull(message = "单词本不能为空")
    @Schema(description = "单词本 ID")
    private Long wordbookId;

    @Schema(description = "用于生成文章的单词本词条 ID 列表")
    private List<Long> entryIds;

    @Schema(description = "文章字数范围：150-200、300-500、500-700、800-1000")
    private String wordCountRange;

    @Schema(description = "文章难度：easy、medium、hard")
    private String difficulty;

    @Schema(description = "学习备注或生成要求，会进入文章生成提示词")
    private String remark;

    @Schema(description = "指定模型配置 ID，可选")
    private Long modelConfigId;

    @Schema(description = "指定 Agent 编码，可选，默认 english_article")
    private String agentCode;

    @Schema(description = "指定提示词模板编码，可选，默认 english_vocab_article_json")
    private String templateCode;

    @Schema(description = "是否强制重新调用 AI")
    private Boolean forceRefresh;
}
