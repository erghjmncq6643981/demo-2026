package com.chandler.fcc.common.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * FNode.ChannelBridge 话道桥接指令入参模型
 * <p>
 * 用于下发两路已建立话道（如客户 Leg 与坐席 Leg）进行实时双向双轨对讲桥接。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "FNode.ChannelBridge 话道双向桥接指令入参")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeBridgeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 控制流程唯一标识
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 主通道 A 端的 Channel UUID
     */
    @Schema(description = "主通道 UUID (通常为客户话道)", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    /**
     * 对端通道 B 端的 Channel UUID
     */
    @JsonProperty("peer_uuid")
    @Schema(description = "对端通道 UUID (通常为坐席话道)", example = "b2c3d4e5-0000-1111-2222-333344445555")
    private String peerUuid;
}
