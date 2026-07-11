package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户偏好设置 DO。
 */
@Data
@TableName("learning_user_preference")
public class LearningUserPreference extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "偏好键，例如 speech.voice_type")
    private String preferenceKey;

    @Schema(description = "偏好值，按业务键决定内容格式")
    private String preferenceValue;
}
