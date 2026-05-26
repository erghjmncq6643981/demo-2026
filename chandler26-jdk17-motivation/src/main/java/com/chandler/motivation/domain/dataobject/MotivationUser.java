package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_user")
public class MotivationUser {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String nickname;
    private String passwordHash;
    private String avatarUrl;
    @JsonIgnore
    @TableField(value = "avatar_data", select = false)
    private byte[] avatarData;
    @JsonIgnore
    @TableField(value = "avatar_content_type", select = false)
    private String avatarContentType;
    private String userType;
    private Integer enabled;
    private LocalDateTime lastLoginTime;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
