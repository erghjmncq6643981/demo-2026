package com.chandler.fcc.server.management.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * 管理员创建自动外呼任务的请求参数，操作权限由服务端认证信息确定。
 */
@Data
@Schema(description = "管理员创建自动外呼任务请求")
public class CreateDialJobReq {

    /**
     * 本次任务的被叫号码。
     */
    @NotBlank(message = "被叫号码不能为空")
    @Schema(description = "被叫电话号码")
    private String number;

    /** 已发布自动外呼流程编码。 */
    @NotBlank(message = "自动外呼流程不能为空")
    @Schema(description = "已发布自动外呼流程编码", example = "SYSTEM_NOTIFICATION")
    private String flowKey;

    /** 传给流程模型的输入变量。 */
    @Schema(description = "流程输入变量；通知文案使用 text，确认按键使用 confirmDigit")
    private Map<String, Object> variables = new LinkedHashMap<>();

    /**
     * 包含首次呼叫在内的最大执行次数。
     */
    @Min(value = 1, message = "最多尝试次数不能小于1")
    @Max(value = 3, message = "最多尝试次数不能大于3")
    @Schema(description = "最多尝试次数，范围为1至3次")
    private int maxAttempts;

    /**
     * 客户端在同一次业务请求重试时保持不变的幂等标识。
     */
    @NotBlank(message = "请求幂等标识不能为空")
    @Schema(description = "客户端生成的唯一请求标识，重试时须复用")
    private String requestKey;
}
