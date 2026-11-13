package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自定义新场景初始化请求。
 */
@Data
@Schema(description = "自定义新场景初始化请求")
public class RuminationCustomScenarioRequest {

    @NotNull(message = "单词本ID不能为空")
    @Schema(description = "关联单词本ID", example = "1")
    private Long wordbookId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "场景唯一标识编码", example = "custom_supermarket")
    private String scenarioCode;

    @NotBlank(message = "场景名称不能为空")
    @Schema(description = "场景中文名称", example = "🛒 零售超市与日常购物")
    private String scenarioName;

    @Schema(description = "场景图标 Emoji", example = "🛒")
    private String scenarioIcon;

    @Schema(description = "场景背景描述与提示词", example = "超市收银台结账、称重贴标、打折优惠券与日常杂货采买")
    private String scenarioPrompt;

    @Schema(description = "客户端幂等提交标识，同一提交重发时用于复用已创建异步任务")
    private String requestId;
}
