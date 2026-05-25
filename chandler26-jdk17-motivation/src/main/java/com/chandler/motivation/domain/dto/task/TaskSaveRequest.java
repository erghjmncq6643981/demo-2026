package com.chandler.motivation.domain.dto.task;

import lombok.Data;

@Data
public class TaskSaveRequest {
    private Long childId;
    private Long goalId;
    private String name;
    private String description;
    private String periodType;
    private String scheduleJson;
    private String taskColor;
    private String pointType;
    private String pointColor;
    private Integer basePoints;
    private Boolean requireApproval;
    private Boolean allowPenalty;
    private Integer sortNo;
}
