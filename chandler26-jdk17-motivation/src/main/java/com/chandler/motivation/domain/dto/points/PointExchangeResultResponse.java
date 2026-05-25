package com.chandler.motivation.domain.dto.points;

import com.chandler.motivation.domain.dataobject.MotivationPointLedger;
import lombok.Data;

@Data
public class PointExchangeResultResponse {
    private String fromPointType;
    private String toPointType;
    private Integer fromAmount;
    private Integer toAmount;
    private PointExchangeRuleResponse exchangeRule;
    private MotivationPointLedger spentLedger;
    private MotivationPointLedger earnedLedger;
}
