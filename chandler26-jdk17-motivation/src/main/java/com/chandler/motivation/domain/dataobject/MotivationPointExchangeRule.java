package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_point_exchange_rule")
public class MotivationPointExchangeRule {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long childId;
    private Integer starWeight;
    private Integer flowerWeight;
    private Integer crownWeight;
    private Long createdByUserId;
    private Long updatedByUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
