package com.chandler.motivation.domain.dto.points;

import lombok.Data;

@Data
public class PointCurrencySaveRequest {
    private Long childId;
    private String pointType;
    private String name;
    private String icon;
    private String color;
    private Integer exchangeWeight;
    private String status;
    private Integer sortNo;
}
