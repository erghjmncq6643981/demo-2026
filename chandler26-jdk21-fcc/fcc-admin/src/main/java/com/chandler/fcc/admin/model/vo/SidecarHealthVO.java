package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Sidecar 与 FreeSWITCH 节点健康状态视图。
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Sidecar 与 FreeSWITCH 节点健康状态")
public class SidecarHealthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "综合状态，取值为 HEALTHY、DEGRADED 或 UNAVAILABLE", example = "HEALTHY")
    private String status;

    @Schema(description = "Sidecar 节点标识", example = "fs-node-sh-01")
    private String nodeId;

    @Schema(description = "节点治理状态", example = "ACTIVE")
    private String nodeState;

    @Schema(description = "FreeSWITCH ESL 是否可用", example = "true")
    private Boolean freeSwitchAlive;

    @Schema(description = "Sidecar PostgreSQL 是否可用", example = "true")
    private Boolean databaseConnected;

    @Schema(description = "当前活跃通道数", example = "12")
    private Integer activeChannels;

    @Schema(description = "节点最大通道数", example = "1000")
    private Integer maxChannels;

    @Schema(description = "管理端完成本次探测的时间")
    private LocalDateTime checkedAt;

    @Schema(description = "不可用或降级原因，不包含敏感连接信息", example = "Sidecar 请求失败")
    private String message;
}
