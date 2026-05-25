package com.chandler.motivation.domain.dto.task;

import java.time.LocalDate;
import lombok.Data;

@Data
public class TaskCompleteRequest {
    private Long taskId;
    private LocalDate taskDate;
    private Integer completionProgress;
    private String remark;
}
