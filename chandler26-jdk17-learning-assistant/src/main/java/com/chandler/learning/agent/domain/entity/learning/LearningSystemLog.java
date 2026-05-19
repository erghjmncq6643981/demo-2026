package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_system_log")
public class LearningSystemLog {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String logType;

    private String title;

    private String detail;

    private String source;

    private String businessType;

    private String businessId;

    private LocalDateTime createTime;
}
