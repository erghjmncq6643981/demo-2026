package com.chandler.motivation.domain.dto.points;

import lombok.Data;

@Data
public class PointAdjustRequest {
    private String pointType;
    private Integer amount;
    private String reason;
}
