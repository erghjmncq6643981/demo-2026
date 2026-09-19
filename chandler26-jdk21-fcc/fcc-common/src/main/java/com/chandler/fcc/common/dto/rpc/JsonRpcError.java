package com.chandler.fcc.common.dto.rpc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * JSON-RPC 2.0 协议标准错误结构体
 * <p>
 * 封装 RPC 请求失败时的错误码、可读描述及详细堆栈或诊断数据。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "JSON-RPC 2.0 标准错误结构体")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class JsonRpcError implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 错误状态码
     */
    @Schema(description = "错误状态码", example = "-32601")
    private Integer code;

    /**
     * 错误原因可读描述
     */
    @Schema(description = "错误原因描述", example = "Method not found")
    private String message;

    /**
     * 附加诊断数据
     */
    @Schema(description = "附加诊断数据", example = "{\"detail\": \"FNode.UnknownMethod does not exist\"}")
    private Object data;
}
