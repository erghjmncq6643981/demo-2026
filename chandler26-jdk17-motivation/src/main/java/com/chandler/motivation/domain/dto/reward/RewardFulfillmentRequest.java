package com.chandler.motivation.domain.dto.reward;

import lombok.Data;

@Data
public class RewardFulfillmentRequest {
    private String fulfillmentStatus;
    private String remark;
}
