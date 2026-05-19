package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemLogResponse {

    private Long id;

    private String type;

    private String title;

    private String detail;

    private String source;

    private String businessType;

    private String businessId;

    private LocalDateTime time;
}
