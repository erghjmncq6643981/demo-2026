package com.chandler.fcc.common.dto.rpc;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * JSON-RPC 2.0 标准请求传输报文
 * <p>
 * 用于 Java 控制面与 Go Sidecar 之间通过 NATS 总线（主题: fs.cmd.{nodeId}）进行异步请求与应答交互。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "JSON-RPC 2.0 标准请求传输报文")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class JsonRpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * JSON-RPC 协议版本号
     */
    @Schema(description = "协议版本号，固定为 2.0", example = "2.0")
    @Builder.Default
    private String jsonrpc = "2.0";

    /**
     * 请求唯一编号
     */
    @Schema(description = "请求唯一标识，用于匹配 RPC 响应", example = "cmd-1789693905001")
    private Object id;

    /**
     * 调用的远程方法名称
     */
    @Schema(description = "远程调用方法名（对应 FNode.*）", example = "FNode.Dial")
    private String method;

    /**
     * 业务请求参数载荷
     */
    @Schema(description = "业务参数对象")
    private Object params;
}
