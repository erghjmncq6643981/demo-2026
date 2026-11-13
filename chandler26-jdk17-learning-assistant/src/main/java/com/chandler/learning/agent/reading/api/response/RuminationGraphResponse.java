package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识反刍场景星系拓扑响应。
 */
@Data
@Schema(name = "知识反刍场景星系拓扑响应")
public class RuminationGraphResponse {

    @Schema(description = "场景编码")
    private String scenarioCode;

    @Schema(description = "场景名称")
    private String scenarioName;

    @Schema(description = "场景图标")
    private String scenarioIcon;

    @Schema(description = "星系图谱节点列表")
    private List<RuminationNodeResponse> nodes;

    @Schema(description = "星系图谱连线列表")
    private List<RuminationEdgeResponse> edges;

    @Schema(description = "当前场景保存的词袋词汇列表")
    private List<String> bagTerms;

    @Schema(description = "场景图谱是否仍在后台异步构建中")
    private Boolean building;

    @Schema(description = "星系图谱当前版本号，未持久化预设场景返回 null")
    private Integer version;
}
