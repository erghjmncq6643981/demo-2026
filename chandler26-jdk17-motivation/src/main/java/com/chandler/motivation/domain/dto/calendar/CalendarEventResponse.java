package com.chandler.motivation.domain.dto.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CalendarEventResponse {
    private Long recordId;
    private Long taskId;
    private Long goalId;
    private Long childId;
    private LocalDate taskDate;
    private String taskName;
    private String taskColor;
    private String pointType;
    private String pointColor;
    private Integer basePoints;
    private String periodType;
    private String scheduleJson;
    private Integer completionProgress;
    private String status;
    private Integer scoreAwarded;
    private Boolean persisted;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
