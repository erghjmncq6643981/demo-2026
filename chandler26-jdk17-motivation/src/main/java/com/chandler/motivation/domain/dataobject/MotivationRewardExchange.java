package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_reward_exchange")
public class MotivationRewardExchange {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long rewardId;
    private Long childId;
    private String rewardNameSnapshot;
    private String rewardColorSnapshot;
    private String rewardIconSnapshot;
    private String requiredPointType;
    private Integer requiredPointsSnapshot;
    private String status;
    private String fulfillmentStatus;
    private String branchStatus;
    private Long requestedByUserId;
    private Long reviewedByUserId;
    private Long fulfillmentUpdatedByUserId;
    private Long deductedLedgerId;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime fulfillmentUpdatedAt;
    private LocalDate expectedArrivalDate;
    private LocalDate scheduleStartDate;
    private LocalDate scheduleEndDate;
    private LocalDateTime completedAt;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
    private String remark;
    private LocalDateTime createTime;
    @TableField(update = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
