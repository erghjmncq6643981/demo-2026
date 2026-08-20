package com.chandler.learning.agent.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户偏好设置 DO。
 */
@Data
@TableName("learning_user_preference")
public class LearningUserPreference extends BaseEntity {

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
     * 偏好键，例如 speech.voice_type。
     */
    @Schema(description = "偏好键，例如 speech.voice_type")
    private String preferenceKey;

    /**
     * 偏好值，按业务键决定内容格式。
     */
    @Schema(description = "偏好值，按业务键决定内容格式")
    private String preferenceValue;
}
