package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_point_ledger")
public class MotivationPointLedger {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long childId;
    private String pointType;
    private Integer changeAmount;
    private Integer balanceAfter;
    private String sourceType;
    private Long sourceId;
    private String sourceName;
    private String reason;
    private Long operatorUserId;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
    @TableField(update = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
}
