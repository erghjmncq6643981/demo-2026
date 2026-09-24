package com.chandler.fcc.server.management.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** 客户接通后播报的本次通知文案。 */
    @NotBlank(message = "通知文案不能为空")
    @Size(max = 1000, message = "通知文案不能超过1000个字符")
    @Schema(description = "本次任务独立的通知文案，由 Sidecar 转为语音", example = "您的服务即将到期，请按1确认")
    private String text;

    /** 客户确认使用的单个按键。 */
    @Pattern(regexp = "[0-9]", message = "确认按键必须是一位数字")
    @Schema(description = "客户确认按键，默认1", example = "1")
    private String confirmDigit = "1";

    /** 等待客户按键的秒数。 */
    @Min(value = 3, message = "确认等待时间不能小于3秒")
    @Max(value = 60, message = "确认等待时间不能大于60秒")
    @Schema(description = "等待客户确认按键的秒数，范围3至60", example = "10")
    private int timeoutSeconds = 10;

    /** 可选业务关联标识。 */
    @Size(max = 128, message = "业务标识不能超过128个字符")
    @Schema(description = "业务系统侧关联标识，可为空", example = "ORDER_202609240001")
    private String bizId;

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
