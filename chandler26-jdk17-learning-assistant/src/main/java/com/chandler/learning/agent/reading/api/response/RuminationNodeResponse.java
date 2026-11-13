package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 知识反刍图谱节点响应。
 */
@Data
@Schema(name = "知识反刍图谱节点响应")
public class RuminationNodeResponse {

    @Schema(description = "节点唯一标识，词条 ID 或衍生标识")
    private String id;

    @Schema(description = "节点类型：hub（场景核心枢纽）、core（考纲词本核心词）、expansion（4D衍生词）")
    private String nodeType;

    @Schema(description = "词汇英文拼写或枢纽名称")
    private String term;

    @Schema(description = "枢纽显示标签")
    private String label;

    @Schema(description = "枢纽次级标签")
    private String subLabel;

    @Schema(description = "国际音标")
    private String phonetic;

    @Schema(description = "中文核心精简释义")
    private String definition;

    @Schema(description = "词本词条 ID，仅 core 节点有效")
    private Long entryId;

    @Schema(description = "4D 衍生维度：action（动作）、downstream（加工产物）、attribute（感官属性）、peer（同类）")
    private String dimension;

    @Schema(description = "掌握状态：mastered（已掌握）、vague（待复习/模糊）、unlearned（未学习）")
    private String status;

    @Schema(description = "熟练度分值（0-100）")
    private Integer masteryScore;

    @Schema(description = "艾宾浩斯复习阶段")
    private Integer reviewStage;

    @Schema(description = "节点在星系画布中的横向物理坐标 X")
    private Double x;

    @Schema(description = "节点在星系画布中的纵向物理坐标 Y")
    private Double y;

    @Schema(description = "节点是否已锁定自定义固定摆放位置")
    private Boolean fixed;
}
