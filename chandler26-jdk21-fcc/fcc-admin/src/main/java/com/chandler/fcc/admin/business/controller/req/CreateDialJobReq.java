package com.chandler.fcc.admin.business.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端创建自动外呼任务的请求参数。
 */
@Data
@Schema(description = "管理端创建自动外呼任务请求")
public class CreateDialJobReq {

    /** 执行任务的坐席工号。 */
    @NotBlank(message = "执行坐席不能为空")
    @Schema(description = "执行任务的坐席工号", example = "901001")
    private String owner;

    /** 被叫号码。 */
    @NotBlank(message = "被叫号码不能为空")
    @Schema(description = "自动外呼的被叫号码", example = "13800138000")
    private String number;

    /** 外呼模式。 */
    @NotBlank(message = "外呼模式不能为空")
    @Schema(description = "外呼模式：PROGRESSIVE 或 NOTIFICATION", example = "PROGRESSIVE")
    private String mode;

    /** 最大尝试次数。 */
    @Min(value = 1, message = "最多尝试次数不能小于1")
    @Max(value = 3, message = "最多尝试次数不能大于3")
    @Schema(description = "包含首次呼叫在内的最大尝试次数", example = "1")
    private int maxAttempts;

    /** 幂等请求标识。 */
    @NotBlank(message = "请求幂等标识不能为空")
    @Schema(description = "同一次业务请求重试时保持不变的幂等标识")
    private String requestKey;
}
