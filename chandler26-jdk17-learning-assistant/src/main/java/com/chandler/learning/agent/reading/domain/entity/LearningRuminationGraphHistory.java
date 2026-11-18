package com.chandler.learning.agent.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识反刍场景星系图谱历史快照版本实体。
 * <p>每个场景保留最近 30 次变更记录，支持误删回溯与一键恢复。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_rumination_graph_history")
@Schema(name = "知识反刍场景星系图谱历史快照")
public class LearningRuminationGraphHistory extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "历史快照主键 ID")
    private Long id;

    @Schema(description = "关联主图谱记录 ID")
    private Long graphId;

    @Schema(description = "所属用户 ID")
    private Long userId;

    @Schema(description = "所属个人单词本 ID")
    private Long wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "快照对应的主图谱版本号")
    private Integer graphVersion;

    @Schema(description = "快照包含的节点总数")
    private Integer nodeCount;

    @Schema(description = "快照包含的连线总数")
    private Integer edgeCount;

    @Schema(description = "快照场景星系节点 JSON")
    private String graphNodesJson;

    @Schema(description = "快照场景星系连线关系 JSON")
    private String graphEdgesJson;

    @Schema(description = "快照词袋中所选词汇列表 JSON")
    private String bagTermsJson;

    @Schema(description = "变更摘要说明（如：手动保存、节点裂变、历史版本恢复等）")
    private String changeSummary;
}
