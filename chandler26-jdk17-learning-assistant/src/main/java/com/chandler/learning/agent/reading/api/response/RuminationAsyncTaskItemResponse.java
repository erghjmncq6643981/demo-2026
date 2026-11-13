package com.chandler.learning.agent.reading.api.response;

import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识反刍工作台异步任务聚合摘要项，涵盖材料编织、自定义场景构建与节点 4D 衍生裂变三类任务。
 */
@Data
@Schema(name = "知识反刍异步任务摘要")
public class RuminationAsyncTaskItemResponse {

    @Schema(description = "底层 AI 异步任务详情")
    private AiAsyncTaskResponse task;

    @Schema(description = "关联个人单词本 ID（作为字符串传输）")
    private String wordbookId;

    @Schema(description = "绑定主题场景编码")
    private String scenarioCode;

    @Schema(description = "绑定主题场景名称")
    private String scenarioName;

    @Schema(description = "自定义场景图标（仅场景构建任务携带）")
    private String scenarioIcon;

    @Schema(description = "绑定父节点 ID（仅节点衍生裂变任务携带）")
    private String parentNodeId;

    @Schema(description = "待裂变目标词汇（仅节点衍生裂变任务携带）")
    private String targetWord;

    @Schema(description = "内容生成来源：ai 默认大模型生成，fallback 本地语义引擎降级")
    private String generationSource;
}
