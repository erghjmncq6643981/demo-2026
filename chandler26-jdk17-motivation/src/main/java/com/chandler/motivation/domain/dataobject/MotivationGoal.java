package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_goal")
public class MotivationGoal {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long childId;
    private String name;
    private String description;
    private String goalColor;
    private String icon;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer targetPoints;
    private String status;
    private Integer deleted;
    private Integer sortNo;
    private Long createdByUserId;
    private Long updatedByUserId;
    private LocalDateTime createTime;
    @TableField(update = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
