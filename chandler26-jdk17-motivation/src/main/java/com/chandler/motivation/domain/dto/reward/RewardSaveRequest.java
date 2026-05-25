package com.chandler.motivation.domain.dto.reward;

import lombok.Data;

@Data
public class RewardSaveRequest {
    private Long childId;
    private String name;
    private String description;
    private String rewardIcon;
    private String rewardColor;
    private String requiredPointType;
    private Integer requiredPoints;
    private Integer stockTotal;
    private String exchangeLimitType;
    private Integer exchangeLimitCount;
    private String fulfillmentType;
    private Boolean requireApproval;
    private Integer sortNo;
}
