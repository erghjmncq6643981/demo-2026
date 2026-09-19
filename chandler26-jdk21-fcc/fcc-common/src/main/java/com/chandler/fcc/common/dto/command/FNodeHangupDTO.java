package com.chandler.fcc.common.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * FNode.Hangup 通话挂机拆线指令入参模型
 * <p>
 * 用于向 FreeSWITCH 下发挂机拆线信令，释放指定通道或整通呼叫。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "FNode.Hangup 通话挂机拆线指令入参")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeHangupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 控制流程唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 目标通道 UUID（若为空则默认挂断该控制流下所有通道）
     */
    @Schema(description = "挂断目标话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 挂机原因码
     */
    @Schema(description = "Q.850 挂机原因码", example = "NORMAL_CLEARING")
    private String cause;
}
