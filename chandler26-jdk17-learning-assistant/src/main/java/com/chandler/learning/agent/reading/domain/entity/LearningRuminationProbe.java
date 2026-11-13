package com.chandler.learning.agent.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识反刍微环境探针题库与作答记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_rumination_probe")
@Schema(name = "知识反刍微环境探针")
public class LearningRuminationProbe extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "关联的场景材料文章 ID")
    private Long materialId;

    @Schema(description = "所属用户 ID")
    private Long userId;

    @Schema(description = "考察核心词汇")
    private String targetWord;

    @Schema(description = "探针题型：context_match/collocation/causality")
    private String probeType;

    @Schema(description = "题干描述")
    private String question;

    @Schema(description = "选项列表 JSON")
    private String optionsJson;

    @Schema(description = "正确选项文本")
    private String correctAnswer;

    @Schema(description = "情境线索提示")
    private String hint;

    @Schema(description = "题目生成来源：ai 默认大模型生成，fallback 本地语义引擎补齐或降级")
    private String generationSource;

    @Schema(description = "用户选择答案")
    private String selectedAnswer;

    @Schema(description = "作答是否正确")
    private Boolean isCorrect;

    @Schema(description = "获得积分")
    private Integer score;

    @Schema(description = "星系网络是否已点亮")
    private Boolean illuminated;

    @Schema(description = "作答时间")
    private LocalDateTime answeredTime;
}
