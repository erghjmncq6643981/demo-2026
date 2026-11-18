package com.chandler.learning.agent.reading.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存场景星系图谱拓扑与词袋状态请求。
 */
@Data
@Schema(name = "保存场景星系图谱与词袋请求")
public class RuminationGraphSaveRequest {

    @NotNull(message = "单词本 ID 不能为空")
    @Schema(description = "个人单词本 ID")
    private Long wordbookId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @NotBlank(message = "星系节点 JSON 不能为空")
    @Schema(description = "当前星系全部节点拓扑 JSON")
    private String graphNodesJson;

    @NotBlank(message = "星系连线 JSON 不能为空")
    @Schema(description = "当前星系全部连线关系 JSON")
    private String graphEdgesJson;

    @Schema(description = "当前词袋中所选词汇列表 JSON")
    private String bagTermsJson;

    @Schema(description = "客户端当前持有的星系图谱版本号（未落库基线可为空），用于乐观并发校验")
    private Integer expectedVersion;

    @Schema(description = "本次保存的变更摘要说明（可选，如：新增节点、删除连线、布局整理等）")
    private String changeSummary;
}
