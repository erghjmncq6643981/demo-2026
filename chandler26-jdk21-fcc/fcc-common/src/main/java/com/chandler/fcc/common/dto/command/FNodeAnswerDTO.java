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
 * FNode.Answer 指定话道应答参数。
 */
@Schema(description = "FNode.Answer 指定话道应答参数")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FNodeAnswerDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 控制流程唯一标识。
     */
    @JsonProperty("ctrl_uuid")
    @Schema(description = "控制流程唯一标识", example = "fcc-inbound-1789693905000-abcd1234")
    private String ctrlUuid;

    /**
     * 需要应答的 FreeSWITCH 话道标识。
     */
    @Schema(description = "需要应答的话道 UUID", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String uuid;
}
