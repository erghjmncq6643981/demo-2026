package com.chandler.fcc.admin.flow.infrastructure.data;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 单通话流程实例的数据库查询投影。
 */
@Getter
@Setter
public class FlowExecutionInstanceData {

    private String id;
    private String callId;
    private String versionId;
    private String status;
    private String currentStep;
    private String snapshot;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
