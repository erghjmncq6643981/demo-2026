package com.chandler.learning.agent.task.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/** AI 任务步骤单次执行摘要。 */
@Data
public class AiAsyncTaskAttemptResponse {

    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "操作人用户标识")
    private Long operatorUserId;
    @Schema(description = "操作人名称")
    private String operatorUserName;
    @Schema(description = "尝试序号")
    private Integer attemptNo;
    @Schema(description = "当前业务状态")
    private String status;
    @Schema(description = "模型配置标识")
    private Long modelConfigId;
    @Schema(description = "AI 供应商")
    private String provider;
    @Schema(description = "模型名称")
    private String modelName;
    @Schema(description = "输入 Token 数")
    private Integer promptTokens;
    @Schema(description = "输出 Token 数")
    private Integer completionTokens;
    @Schema(description = "总 Token 数")
    private Integer totalTokens;
    @Schema(description = "耗时（毫秒）")
    private Long costTime;
    @Schema(description = "错误码")
    private String errorCode;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "开始时间")
    private LocalDateTime startedTime;
    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;
}
