package com.chandler.fcc.server.management.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理业务接口的成功响应，业务失败由统一异常处理器返回。
 *
 * @param <T> 业务结果类型
 */
@Data
@Schema(description = "管理业务成功响应")
public class ManagementResp<T> {

    @Schema(description = "成功状态码，固定为200")
    private int code = 200;

    @Schema(description = "本次管理操作的业务结果")
    private T data;
}
