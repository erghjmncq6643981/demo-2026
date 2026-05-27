package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户偏好配置，用于记住操作者的当前宝贝、日历视图等使用习惯。
 */
@Data
@TableName("motivation_user_preference")
public class MotivationUserPreference {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String preferenceKey;
    private String preferenceValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
