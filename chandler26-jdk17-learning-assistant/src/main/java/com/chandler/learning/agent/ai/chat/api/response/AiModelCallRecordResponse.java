package com.chandler.learning.agent.ai.chat.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/** 模型调用审计详情。 */
@Data
public class AiModelCallRecordResponse {

    @Schema(description = "主键标识")
    private Long id;
    @Schema(description = "Agent 编码")
    private String agentCode;
    @Schema(description = "编码")
    private String invocationSceneCode;
    @Schema(description = "AI 供应商")
    private String provider;
    @Schema(description = "模型名称")
    private String modelName;
    @Schema(description = "请求 JSON")
    private String requestJson;
    @Schema(description = "模型响应 JSON")
    private String responseJson;
    @Schema(description = "是否成功")
    private Boolean success;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "输入 Token 数")
    private Integer promptTokens;
    @Schema(description = "输出 Token 数")
    private Integer completionTokens;
    @Schema(description = "总 Token 数")
    private Integer totalTokens;
    @Schema(description = "调用耗时（毫秒）")
    private Long latencyMs;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
