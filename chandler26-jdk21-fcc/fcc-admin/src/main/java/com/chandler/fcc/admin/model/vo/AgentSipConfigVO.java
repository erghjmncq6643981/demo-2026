package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.AllArgsConstructor;

/** 当前认证坐席专属 SIP 注册配置，不得持久化到浏览器或记录日志。 */
@Getter
@AllArgsConstructor
@Schema(description = "当前坐席 SIP 注册配置，仅限本人读取")
public class AgentSipConfigVO {
    @Schema(description = "授权的 WebRTC 分机")
    private final String extension;
    @Schema(description = "SIP WebSocket 地址")
    private final String wsUrl;
    @Schema(description = "SIP 注册域")
    private final String domain;
    @Schema(description = "本人分机注册凭据，只保存在内存")
    private final String password;
}
