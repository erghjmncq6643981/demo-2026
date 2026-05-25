package com.chandler.motivation.domain.dto.points;

import lombok.Data;

@Data
public class PointExchangeRequest {
    private String fromPointType;
    private String toPointType;
    private Integer fromAmount;
}
