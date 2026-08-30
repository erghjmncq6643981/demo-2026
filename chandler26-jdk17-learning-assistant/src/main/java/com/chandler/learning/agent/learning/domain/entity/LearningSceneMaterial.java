package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * AI 生成的场景学习材料及完整结构化结果。
 */
@Data
@TableName("learning_scene_material")
public class LearningSceneMaterial extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习场景单元 ID。 */
    private Long unitId;

    /** 单元内材料版本号。 */
    private Integer revisionNo;

    /** 材料状态：draft、published、archived、failed。 */
    private String materialStatus;

    /** 是否为单元当前生效版本。 */
    private Boolean currentVersion;

    /** 上一版本材料 ID。 */
    private Long supersedesMaterialId;

    /** AI 会话 ID。 */
    private Long sessionId;

    /** 标题。 */
    private String title;

    /** 场景类型。 */
    private String scenarioType;

    /** 场景英文学习材料。 */
    private String learningText;

    /** 中文译文。 */
    private String translation;

    /** AI 原始响应内容。 */
    private String rawContent;

    /** 解析后的结构化 JSON。 */
    private String parsedJson;

    /** 模型供应商。 */
    private String provider;

    /** 模型名称。 */
    private String modelName;

    /** 模型调用 Token 总数。 */
    private Integer tokenUsage;

    /** 处理耗时，单位毫秒。 */
    private Long costTime;
}
