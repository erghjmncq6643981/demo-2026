package com.chandler.learning.agent.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统日志 DO。
 * <p>
 * 同时承载运行日志与业务日志，前端个人信息页可查看。
 */
@Data
@TableName("learning_system_log")
public class LearningSystemLog extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 用户 ID。
     */
    @Schema(description = "用户 ID")
    private Long userId;

    /**
     * 日志类型：auth/ai/cache/review/wordbook/error 等。
     */
    @Schema(description = "日志类型：auth/ai/cache/review/wordbook/error 等")
    private String logType;

    /**
     * 业务人员可理解的日志标题。
     */
    @Schema(description = "业务人员可理解的日志标题")
    private String title;

    /**
     * 日志详情。
     */
    @Schema(description = "日志详情")
    private String detail;

    /**
     * 日志来源：server/client。
     */
    @Schema(description = "日志来源：server/client")
    private String source;

    /**
     * 业务类型。
     */
    @Schema(description = "业务类型")
    private String businessType;

    /**
     * 业务 ID。
     */
    @Schema(description = "业务 ID")
    private String businessId;
}
