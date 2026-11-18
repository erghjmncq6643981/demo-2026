package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识反刍场景星系图谱历史快照版本摘要响应。
 */
@Data
@Schema(name = "知识反刍场景星系图谱历史版本摘要响应")
public class RuminationGraphHistoryItemResponse {

    @Schema(description = "历史快照主键 ID（字符串格式）")
    private String id;

    @Schema(description = "关联主图谱记录 ID（字符串格式）")
    private String graphId;

    @Schema(description = "所属个人单词本 ID（字符串格式）")
    private String wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "该快照对应的图谱版本号")
    private Integer graphVersion;

    @Schema(description = "该快照包含的节点总数")
    private Integer nodeCount;

    @Schema(description = "该快照包含的连线总数")
    private Integer edgeCount;

    @Schema(description = "变更摘要说明")
    private String changeSummary;

    @Schema(description = "快照创建时间")
    private LocalDateTime createTime;
}
