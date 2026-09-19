package com.chandler.fcc.common.dto.rpc;

import com.chandler.fcc.common.entity.FNodeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * JSON-RPC 2.0 标准响应传输报文
 * <p>
 * Go Sidecar 处理完 FNode.* 指令后向 NATS Reply-To 主题投递的标准化应答报文。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "JSON-RPC 2.0 标准响应传输报文")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class JsonRpcResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * JSON-RPC 协议版本号
     */
    @Schema(description = "协议版本号，固定为 2.0", example = "2.0")
    @Builder.Default
    private String jsonrpc = "2.0";

    /**
     * 对应的请求唯一标识
     */
    @Schema(description = "匹配的请求唯一标识", example = "cmd-1789693905001")
    private Object id;

    /**
     * 执行成功的返回结果体
     */
    @Schema(description = "FNode 执行结果数据")
    private FNodeResult result;

    /**
     * 执行失败时的错误结构体
     */
    @Schema(description = "错误详情对象，成功时为 null")
    private JsonRpcError error;

    /**
     * 判断响应是否成功
     *
     * @return 若 error 为空且 result 执行成功则返回 true
     */
    public boolean isSuccess() {
        return error == null && result != null && result.isSuccess();
    }
}
