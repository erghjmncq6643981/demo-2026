package com.chandler.learning.agent.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习助手用户 DO。
 */
@Data
@TableName("learning_user")
public class LearningUser extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 登录账号。
     */
    @Schema(description = "登录账号")
    private String username;

    /**
     * 展示昵称。
     */
    @Schema(description = "展示昵称")
    private String nickname;

    /**
     * 手机号，数据库保存明文，接口输出由后端脱敏。
     */
    @Schema(description = "手机号，数据库保存明文，接口输出由后端脱敏")
    private String phone;

    /**
     * 联系邮箱，接口输出由后端脱敏。
     */
    @Schema(description = "联系邮箱，接口输出由后端脱敏")
    private String email;

    /**
     * 密码哈希，格式包含算法、盐和摘要。
     */
    @Schema(description = "密码哈希，格式包含算法、盐和摘要")
    private String passwordHash;

    /**
     * 账户是否启用。
     */
    @Schema(description = "账户是否启用")
    private Boolean enabled;

    /**
     * 系统角色编码：USER-普通学习者，ADMIN-系统管理员。
     */
    @Schema(description = "系统角色编码：USER-普通学习者，ADMIN-系统管理员")
    private String roleCode;

    /**
     * 最近登录时间。
     */
    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;
}
