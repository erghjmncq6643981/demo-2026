package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_family_member")
public class MotivationFamilyMember {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long childId;
    private Long userId;
    private String relationRole;
    private Integer isPrimary;
    private Integer canManage;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
