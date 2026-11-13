package com.chandler.learning.agent.reading.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识反刍场景材料文章实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_rumination_material")
@Schema(name = "知识反刍场景材料文章")
public class LearningRuminationMaterial extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "所属用户 ID")
    private Long userId;

    @Schema(description = "所属单词本 ID")
    private Long wordbookId;

    @Schema(description = "主题场景编码")
    private String scenarioCode;

    @Schema(description = "场景文章标题")
    private String title;

    @Schema(description = "场景语境背景概述")
    private String scenarioSummary;

    @Schema(description = "英文原文正文")
    private String articleContent;

    @Schema(description = "中文对照翻译")
    private String translationContent;

    @Schema(description = "本次反刍所选目标词汇列表 JSON")
    private String targetWordsJson;

    @Schema(description = "正文字数")
    private Integer wordCount;

    @Schema(description = "内容生成来源：ai 默认大模型生成，fallback 本地语义引擎降级")
    private String generationSource;

    @Schema(description = "关联异步生成任务 ID，用于断点续跑与幂等落库")
    private Long taskId;

    @Schema(description = "AI 合成语音音频存储相对路径")
    private String audioPath;

    @Schema(description = "语音合成状态：none 未生成，pending 排队中，running 合成中，completed 已就绪，failed 失败")
    private String audioStatus;
}
