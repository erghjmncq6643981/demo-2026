package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 英语词汇学习请求。
 */
@Data
@Schema(name = "英语词汇学习请求")
public class VocabularyStudyRequest {

    @NotBlank(message = "单词不能为空")
    private String term;

    @Schema(description = "Agent 编码")
    private String agentCode = "english_vocabulary";

    @Schema(description = "提示词模板编码")
    private String templateCode = "english_vocab_card_json";

    @Schema(description = "指定 AI 模型配置 ID")
    private Long modelConfigId;

    @Schema(description = "是否强制重新调用 AI")
    private Boolean forceRefresh = false;
}
