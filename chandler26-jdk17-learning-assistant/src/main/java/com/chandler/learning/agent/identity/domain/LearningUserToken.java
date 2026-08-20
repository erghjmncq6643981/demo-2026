package com.chandler.learning.agent.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录令牌 DO。
 */
@Data
@TableName("learning_user_token")
public class LearningUserToken extends BaseEntity {

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
     * 访问令牌 SHA-256 哈希。
     */
    @Schema(description = "访问令牌 SHA-256 哈希")
    private String tokenHash;

    /**
     * 令牌过期时间。
     */
    @Schema(description = "令牌过期时间")
    private LocalDateTime expiredTime;

    /**
     * 是否已主动注销。
     */
    @Schema(description = "是否已主动注销")
    private Boolean revoked;
}
