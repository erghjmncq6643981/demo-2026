package com.chandler.learning.agent.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识反刍场景星系图谱拓扑与词袋状态实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_rumination_graph")
@Schema(name = "知识反刍场景星系图谱状态")
public class LearningRuminationGraph extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属用户 ID")
    private Long userId;

    @Schema(description = "所属单词本 ID")
    private Long wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "当前场景星系节点 JSON")
    private String graphNodesJson;

    @Schema(description = "当前场景星系连线关系 JSON")
    private String graphEdgesJson;

    @Schema(description = "当前词袋中所选词汇列表 JSON")
    private String bagTermsJson;
}
