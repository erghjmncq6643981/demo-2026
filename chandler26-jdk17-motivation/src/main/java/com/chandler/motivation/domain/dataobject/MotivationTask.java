package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_task")
public class MotivationTask {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long goalId;
    private Long childId;
    private String name;
    private String description;
    private String periodType;
    private String scheduleJson;
    private String taskColor;
    private String pointType;
    private String pointColor;
    private Integer basePoints;
    private Integer requireApproval;
    private Integer allowPenalty;
    private String status;
    private Integer deleted;
    private Integer sortNo;
    private Long createdByUserId;
    private Long updatedByUserId;
    private LocalDateTime createTime;
    @TableField(update = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
