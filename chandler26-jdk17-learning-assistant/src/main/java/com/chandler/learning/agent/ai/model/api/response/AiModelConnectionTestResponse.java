package com.chandler.learning.agent.ai.model.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * AI 模型连接测试结果。
 * <p>
 * 只返回诊断所需的摘要，不返回 API Key、完整请求或供应商原始响应。
 */
@Data
public class AiModelConnectionTestResponse {

    /** 测试是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 供应商编码。 */
    @Schema(description = "AI 供应商")
    private String provider;

    /** 实际调用的模型名称。 */
    @Schema(description = "模型名称")
    private String modelName;

    /** 请求耗时，单位毫秒。 */
    @Schema(description = "调用耗时（毫秒）")
    private Long latencyMs;

    /** 面向管理员的可读结果。 */
    @Schema(description = "提示消息")
    private String message;

    /** 模型返回内容的短摘要。 */
    @Schema(description = "响应摘要")
    private String responsePreview;
}
