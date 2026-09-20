package com.chandler.fcc.common.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FNode.NativeAPI 受限原生 FreeSWITCH 指令参数。
 */
@Schema(description = "FNode 受限原生 FreeSWITCH 指令参数")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FNodeNativeApiDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Sidecar 白名单中的 FreeSWITCH API 名称", example = "uuid_hold")
    private String cmd;

    @Schema(description = "经过业务服务校验的原生 API 参数", example = "a1b2c3d4-0000-1111-2222-333344445555")
    private String args;
}
