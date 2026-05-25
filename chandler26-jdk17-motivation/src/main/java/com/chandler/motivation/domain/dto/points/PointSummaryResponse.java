package com.chandler.motivation.domain.dto.points;

import com.chandler.motivation.domain.dataobject.MotivationPointCurrency;
import java.util.List;
import lombok.Data;

@Data
public class PointSummaryResponse {
    private Long childId;
    private List<PointBalanceResponse> balances;
    private PointExchangeRuleResponse exchangeRule;
    private List<MotivationPointCurrency> currencies;
}
