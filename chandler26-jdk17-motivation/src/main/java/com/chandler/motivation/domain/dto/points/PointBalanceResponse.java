package com.chandler.motivation.domain.dto.points;

import lombok.Data;

@Data
public class PointBalanceResponse {
    private String pointType;
    private Integer balance;
    private Integer earnedTotal;
    private Integer spentTotal;
}
