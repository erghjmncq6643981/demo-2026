package com.chandler.motivation.domain.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    @TableField(value = "avatar_data", select = false)
    private byte[] avatarData;
    @JsonIgnore
    @TableField(value = "avatar_content_type", select = false)
    private String avatarContentType;
    private LocalDate birthday;
    private String gender;
    private String remark;
    private String status;
    private Integer deleted;
    private Long createdByUserId;
    private LocalDateTime createTime;
    @TableField(update = "CURRENT_TIMESTAMP")
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private Long childAccountUserId;
    @TableField(exist = false)
    private String childUsername;
}
