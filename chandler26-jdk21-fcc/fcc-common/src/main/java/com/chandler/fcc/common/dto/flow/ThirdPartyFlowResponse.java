package com.chandler.fcc.common.dto.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 第三方流程服务必须返回的固定协议响应。
 *
 * <p>HTTP 2xx 只代表网络层成功；FCC 仍会校验协议版本、命令标识和
 * {@code accepted}，避免把任意 JSON 或 HTML 当成业务成功。</p>
 */
@Schema(description = "FCC 第三方流程动作响应协议")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyFlowResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 协议版本。
     */
    @JsonProperty("protocol_version")
    @Schema(description = "第三方流程协议版本，必须与请求一致", example = "1.0")
    private String protocolVersion;

    /**
     * 与请求对应的稳定命令标识。
     */
    @JsonProperty("command_id")
    @Schema(description = "对应请求的稳定幂等命令标识", example = "cmd-0199a1b2c3d4")
    private String commandId;

    /**
     * 第三方是否已经受理动作。
     */
    @Schema(description = "第三方是否受理动作", example = "true")
    private Boolean accepted;

    /**
     * 第三方业务结果码。
     */
    @Schema(description = "第三方业务结果码", example = "OK")
    private String code;

    /**
     * 面向运行日志和流程轨迹的可读消息。
     */
    @Schema(description = "第三方处理结果说明", example = "已创建回拨任务")
    private String message;

    /**
     * 第三方返回的结构化业务结果。
     */
    @Schema(description = "第三方动作结果数据")
    private JsonNode data;

    /**
     * 失败时是否允许使用同一 commandId 重试。
     */
    @JsonProperty("retryable")
    @Schema(description = "当前失败是否允许使用同一命令标识重试", example = "false")
    private Boolean retryable;
}
