package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识反刍图谱连线响应。
 */
@Data
@Schema(name = "知识反刍图谱连线响应")
public class RuminationEdgeResponse {

    @Schema(description = "起点节点标识")
    private String source;

    @Schema(description = "终点节点标识")
    private String target;

    @Schema(description = "语义关系类型：peer（同类/核心）、action（动作动词）、downstream（下游加工）、attribute（感官属性）")
    private String relationType;

    @Schema(description = "连线中央展示的简短微标签，如 '🔪 削皮'")
    private String label;

    @Schema(description = "连线形态样式：curve（平滑曲线）、straight（直线连接）")
    private String lineStyle;

    @Schema(description = "是否采用贝塞尔平滑曲线")
    private Boolean isCurved;

    @Schema(description = "是否在末端渲染方向箭头")
    private Boolean showArrow;
}
