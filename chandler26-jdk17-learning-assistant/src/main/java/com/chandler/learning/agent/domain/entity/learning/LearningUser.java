package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习助手用户 DO。
 */
@Data
@TableName("learning_user")
public class LearningUser extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "展示昵称")
    private String nickname;

    @Schema(description = "手机号，数据库保存明文，接口输出由后端脱敏")
    private String phone;

    @Schema(description = "联系邮箱，接口输出由后端脱敏")
    private String email;

    @Schema(description = "密码哈希，格式包含算法、盐和摘要")
    private String passwordHash;

    @Schema(description = "账户是否启用")
    private Boolean enabled;

    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;
}
