package com.chandler.learning.agent.system.api;

import lombok.Data;

/**
 * SystemLogRequest 类。
 */
@Data
public class SystemLogRequest {

    private String type;

    private String title;

    private String detail;

    private String source;

    private String businessType;

    private String businessId;
}
