package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识反刍 4D 衍生裂变响应。
 */
@Data
@Schema(name = "知识反刍 4D 衍生裂变响应")
public class RuminationFissionResponse {

    @Schema(description = "父节点唯一标识")
    private String parentNodeId;

    @Schema(description = "待裂变的目标词汇")
    private String targetWord;

    @Schema(description = "是否由默认 AI 模型实时生成")
    private Boolean aiPowered;

    @Schema(description = "本次裂变新增的 4D 衍生节点列表")
    private List<RuminationNodeResponse> nodes;

    @Schema(description = "本次裂变新增的星系连线列表")
    private List<RuminationEdgeResponse> edges;

    @Schema(description = "归属个人单词本 ID（作为字符串传输）")
    private String wordbookId;

    @Schema(description = "归属主题场景编码")
    private String scenarioCode;

    @Schema(description = "应用结果状态：applied 已写入原场景，skipped_parent_missing 父节点已不存在未落库，skipped_graph_missing 原场景图谱不存在")
    private String applyStatus;

    @Schema(description = "裂变落库更新后的星系图谱版本号")
    private Integer graphVersion;
}
