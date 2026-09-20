package com.chandler.fcc.admin.flow.infrastructure.data;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 单次流程阶段执行事实的数据库查询投影。
 */
@Getter
@Setter
public class FlowExecutionStepData {

    private String id;
    private String stepKey;
    private String actionType;
    private Integer attemptNo;
    private String status;
    private String commandId;
    private String eventId;
    private String input;
    private String output;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
}
