package com.chandler.motivation.domain.dto.goal;

import java.time.LocalDate;
import lombok.Data;

@Data
public class GoalSaveRequest {
    private Long childId;
    private String name;
    private String description;
    private String goalColor;
    private String icon;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer targetPoints;
    private Integer sortNo;
}
