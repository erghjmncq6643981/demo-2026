package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 知识反刍词袋单项增量变更请求。
 */
@Data
@Schema(name = "知识反刍词袋单项增量更新请求")
public class RuminationGraphBagUpdateRequest {

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "个人单词本 ID")
    private Long wordbookId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @NotBlank(message = "操作词汇或节点标识不能为空")
    @Schema(description = "待加入或移除的英文词条或节点 ID")
    private String term;

    @NotNull(message = "操作类型不能为空")
    @Schema(description = "变更动作：true 加入词袋，false 从词袋移除")
    private Boolean add;
}
