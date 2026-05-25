package com.chandler.motivation.domain.dto.points;

import lombok.Data;

@Data
public class PointExchangeRuleResponse {
    private Long childId;
    private Integer starWeight;
    private Integer flowerWeight;
    private Integer crownWeight;
}
