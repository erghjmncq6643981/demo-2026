package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_task_record")
public class MotivationTaskRecord {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long taskId;
    private Long goalId;
    private Long childId;
    private LocalDate taskDate;
    private String taskNameSnapshot;
    private String taskColorSnapshot;
    private String pointTypeSnapshot;
    private String pointColorSnapshot;
    private Integer basePointsSnapshot;
    private String scheduleSnapshotJson;
    private String ruleSnapshotJson;
    private Integer completionProgress;
    private String status;
    private String sourceType;
    private Long submittedByUserId;
    private LocalDateTime submittedAt;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private String reviewRemark;
    private Integer scoreAwarded;
    private String attachmentJson;
    private Integer deleted;
    private LocalDateTime createTime;
    @TableField(update = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
