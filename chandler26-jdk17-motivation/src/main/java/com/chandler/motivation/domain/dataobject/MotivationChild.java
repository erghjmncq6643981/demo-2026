package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("motivation_child")
public class MotivationChild {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String nickname;
    private String avatarUrl;
    private LocalDate birthday;
    private String gender;
    private String remark;
    private String status;
    private Integer deleted;
    private Long createdByUserId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
