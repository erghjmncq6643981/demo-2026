package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_user_token")
public class LearningUserToken {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String tokenHash;

    private LocalDateTime expiredTime;

    private Boolean revoked;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
