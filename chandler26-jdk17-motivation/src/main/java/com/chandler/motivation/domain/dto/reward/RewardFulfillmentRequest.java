package com.chandler.motivation.domain.dto.reward;

import java.time.LocalDate;
import lombok.Data;

@Data
public class RewardFulfillmentRequest {
    private String fulfillmentStatus;
    private String branchStatus;
    private LocalDate expectedArrivalDate;
    private LocalDate scheduleStartDate;
    private LocalDate scheduleEndDate;
    private String remark;
}
