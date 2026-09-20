package com.chandler.fcc.common.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FNode.Transfer 话道转接指令参数。
 */
@Schema(description = "FNode 话道转接指令参数")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FNodeTransferDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-outbound-0199a1b2c3d4")
    private String ctrlUuid;

    @Schema(description = "待转接话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;

    @Schema(description = "受控转接目标号码或分机", example = "901001")
    private String target;

    @Schema(description = "FreeSWITCH 拨号计划", example = "XML")
    private String dialplan;

    @Schema(description = "FreeSWITCH 拨号上下文", example = "default")
    private String context;
}
