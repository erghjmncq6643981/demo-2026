package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席客户端硬件指纹安全审计记录视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户端硬件指纹审计展示视图")
public class ClientHardwareRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "硬件记录主键 ID", example = "301")
    private Long id;

    @Schema(description = "坐席 ID", example = "10001")
    private Long agentId;

    @Schema(description = "坐席工号", example = "901001")
    private String workNum;

    @Schema(description = "客户端版本号", example = "1.2.0")
    private String clientVersion;

    @Schema(description = "网卡物理 MAC 地址", example = "00:1A:2B:3C:4D:5E")
    private String macAddr;

    @Schema(description = "客户端操作系统", example = "Windows 11 Pro 64-bit")
    private String os;

    @Schema(description = "登录网络 IP", example = "192.168.1.150")
    private String ipAddr;

    @Schema(description = "客户端登录上报时间")
    private LocalDateTime loginTime;
}
