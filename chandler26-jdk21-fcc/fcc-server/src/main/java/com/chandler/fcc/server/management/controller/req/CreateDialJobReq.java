package com.chandler.fcc.server.management.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员创建自动外呼任务的请求参数，操作权限由服务端认证信息确定。
 */
@Data
@Schema(description = "管理员创建自动外呼任务请求")
public class CreateDialJobReq {

    /**
     * 负责执行任务的坐席工号。
     */
    @Schema(description = "执行坐席工号，必须是管理员有权操作的启用坐席")
    private String owner;

    /**
     * 本次任务的被叫号码。
     */
    @Schema(description = "被叫电话号码")
    private String number;

    /**
     * 自动外呼执行模式。
     */
    @Schema(description = "外呼模式：PROGRESSIVE 为坐席先接，NOTIFICATION 为通知外呼")
    private String mode;

    /**
     * 包含首次呼叫在内的最大执行次数。
     */
    @Schema(description = "最多尝试次数，范围为1至3次")
    private int maxAttempts;

    /**
     * 客户端在同一次业务请求重试时保持不变的幂等标识。
     */
    @Schema(description = "客户端生成的唯一请求标识，重试时须复用")
    private String requestKey;
}
