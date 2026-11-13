package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 知识反刍 4D 衍生裂变请求。
 */
@Data
@Schema(name = "知识反刍 4D 衍生裂变请求")
public class RuminationFissionRequest {

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "个人单词本 ID")
    private Long wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "主题场景名称")
    private String scenarioName;

    @Schema(description = "待裂变的父节点标识")
    private String parentNodeId;

    @NotBlank(message = "待裂变目标词汇不能为空")
    @Schema(description = "待裂变的目标英文词汇")
    private String targetWord;

    @Schema(description = "目标词汇中文释义")
    private String targetDefinition;

    @Schema(description = "星系图谱中已存在的词汇列表")
    private List<String> existingTerms;

    @Schema(description = "客户端幂等提交标识，同一提交重发时用于复用已创建异步任务")
    private String requestId;
}
