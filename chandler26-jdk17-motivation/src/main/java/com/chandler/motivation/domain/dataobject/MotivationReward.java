package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_reward")
public class MotivationReward {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long childId;
    private String name;
    private String description;
    private String rewardIcon;
    private String rewardColor;
    private String requiredPointType;
    private Integer requiredPoints;
    private Integer stockTotal;
    private Integer stockRemaining;
    private String exchangeLimitType;
    private Integer exchangeLimitCount;
    private Integer requireApproval;
    private String status;
    private Integer deleted;
    private Integer sortNo;
    private Long createdByUserId;
    private Long updatedByUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
