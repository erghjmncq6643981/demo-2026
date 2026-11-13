package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 知识反刍星系节点坐标增量更新请求。
 */
@Data
@Schema(name = "知识反刍星系节点坐标增量更新请求")
public class RuminationGraphPositionsUpdateRequest {

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "个人单词本 ID")
    private Long wordbookId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @NotEmpty(message = "待更新的节点坐标列表不能为空")
    @Valid
    @Schema(description = "本次增量修改坐标的节点列表")
    private List<NodePositionRequest> positions;

    /**
     * 单个节点的固定坐标更新载荷。
     */
    @Data
    @Schema(name = "节点坐标更新项")
    public static class NodePositionRequest {

        @NotBlank(message = "节点 ID 不能为空")
        @Schema(description = "图谱节点唯一标识 ID")
        private String nodeId;

        @NotNull(message = "节点横向坐标 X 不能为空")
        @Schema(description = "星系画布横向坐标 X")
        private Double x;

        @NotNull(message = "节点纵向坐标 Y 不能为空")
        @Schema(description = "星系画布纵向坐标 Y")
        private Double y;

        @Schema(description = "是否锁定节点拖拽后的坐标")
        private Boolean fixed = true;
    }
}
