package com.chandler.learning.agent.ai.model.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模型配置响应。
 */
@Data
public class AiModelConfigResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "业务对象名称")
    private String name;

    @Schema(description = "AI 供应商")
    private String provider;

    @Schema(description = "模型名称")
    private String modelName;

    /** 当前配置是否属于可用于新任务的模型枚举。 */
    @Schema(description = "是否支持")
    private Boolean supported;

    /** 供应商展示名称。 */
    @Schema(description = "供应商名称")
    private String providerName;

    /** 模型展示名称。 */
    @Schema(description = "模型展示名称")
    private String modelDisplayName;

    /** API 协议编码。 */
    @Schema(description = "接口协议")
    private String apiProtocol;

    /** 调用前请求适配器编码。 */
    @Schema(description = "请求适配器")
    private String requestAdapter;

    /** 调用后结构化响应解析器编码。 */
    @Schema(description = "响应解析器")
    private String responseParser;

    /** 模型原生上下文窗口，单位为 Token。 */
    @Schema(description = "上下文窗口 Token 上限")
    private Integer contextWindowTokens;

    /** 模型单次最大输出，单位为 Token。 */
    @Schema(description = "最大输出 Token 数")
    private Integer maxOutputTokens;

    @Schema(description = "服务地址")
    private String baseUrl;

    @Schema(description = "聊天接口路径")
    private String chatPath;

    @Schema(description = "脱敏 API Key")
    private String apiKeyMasked;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否默认")
    private Boolean isDefault;

    @Schema(description = "排序序号")
    private Integer sequence;

    /** 当前绑定该配置的未删除 Agent 数量。 */
    @Schema(description = "已绑定 Agent 数量")
    private Long boundAgentCount;

    /** 当前绑定该配置的 Agent 名称，供管理端识别影响范围。 */
    @Schema(description = "已绑定 Agent 名称列表")
    private List<String> boundAgentNames;

    /** 累计模型调用次数。 */
    @Schema(description = "调用次数")
    private Long callCount;

    /** 成功调用次数。 */
    @Schema(description = "处理成功数量")
    private Long successCount;

    /** 失败调用次数。 */
    @Schema(description = "失败数量")
    private Long failedCount;

    /** 累计消耗 Token。 */
    @Schema(description = "总 Token 数")
    private Long totalTokens;

    /** 平均模型响应耗时，单位毫秒。 */
    @Schema(description = "平均耗时（毫秒）")
    private Long averageLatencyMs;

    /** 最近一次调用时间。 */
    @Schema(description = "最近调用时间")
    private LocalDateTime lastCallTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
