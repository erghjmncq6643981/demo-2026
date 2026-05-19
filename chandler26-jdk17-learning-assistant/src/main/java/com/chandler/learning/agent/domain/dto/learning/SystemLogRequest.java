package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class SystemLogRequest {

    private String type;

    private String title;

    private String detail;

    private String source;

    private String businessType;

    private String businessId;
}
