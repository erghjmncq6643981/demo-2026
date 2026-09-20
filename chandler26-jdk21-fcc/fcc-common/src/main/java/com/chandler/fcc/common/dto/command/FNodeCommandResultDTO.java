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
 * FNode.CommandResult 已有命令结果查询参数。
 */
@Schema(description = "FNode 已有命令结果查询参数")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FNodeCommandResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("command_id")
    @Schema(description = "首次下发时使用的稳定命令标识", example = "cmd-0199a1b2c3d4")
    private String commandId;
}
