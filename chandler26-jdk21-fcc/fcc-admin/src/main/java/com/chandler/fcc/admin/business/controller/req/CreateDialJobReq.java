package com.chandler.fcc.admin.business.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * 管理端创建自动外呼任务的请求参数。
 */
@Data
@Schema(description = "管理端创建自动外呼任务请求")
public class CreateDialJobReq {

    /** 被叫号码。 */
    @NotBlank(message = "被叫号码不能为空")
    @Schema(description = "自动外呼的被叫号码", example = "13800138000")
    private String number;

    /** 已发布自动外呼流程编码。 */
    @NotBlank(message = "自动外呼流程不能为空")
    @Schema(description = "已发布自动外呼流程编码", example = "SYSTEM_NOTIFICATION")
    private String flowKey;

    /** 传给流程模型的输入变量。 */
    @Schema(description = "流程输入变量；通知文案使用 text，确认按键使用 confirmDigit")
    private Map<String, Object> variables = new LinkedHashMap<>();

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
