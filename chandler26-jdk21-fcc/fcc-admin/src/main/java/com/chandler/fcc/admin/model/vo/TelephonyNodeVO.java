package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通信节点健康与监控视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通信节点运行状态展示视图")
public class TelephonyNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点主键 ID", example = "1")
    private Long id;

    @Schema(description = "节点编码", example = "fs-node-01")
    private String nodeId;

    @Schema(description = "集群标识", example = "default")
    private String clusterId;

    @Schema(description = "通信主机地址", example = "127.0.0.1")
    private String host;

    @Schema(description = "可用区", example = "zone-a")
    private String zone;

    @Schema(description = "运行状态 (ONLINE, OFFLINE, DRAINING)", example = "ONLINE")
    private String status;

    @Schema(description = "最大支持并发路数", example = "1000")
    private Integer maxChannels;

    @Schema(description = "当前活跃通话并发路数", example = "12")
    private Integer activeChannels;

    @Schema(description = "最后心跳上报时间")
    private LocalDateTime lastHeartbeatAt;
}
