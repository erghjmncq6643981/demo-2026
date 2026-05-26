package com.chandler.motivation.domain.dto.log;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChildActivityLogResponse {
    private Long id;
    private Long childId;
    private String childNickname;
    private String logType;
    private String title;
    private String detail;
    private LocalDateTime createTime;
}
