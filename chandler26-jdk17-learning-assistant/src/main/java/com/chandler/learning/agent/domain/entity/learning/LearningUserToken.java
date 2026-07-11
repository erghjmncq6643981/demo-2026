package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录令牌 DO。
 */
@Data
@TableName("learning_user_token")
public class LearningUserToken extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "访问令牌 SHA-256 哈希")
    private String tokenHash;

    @Schema(description = "令牌过期时间")
    private LocalDateTime expiredTime;

    @Schema(description = "是否已主动注销")
    private Boolean revoked;
}
