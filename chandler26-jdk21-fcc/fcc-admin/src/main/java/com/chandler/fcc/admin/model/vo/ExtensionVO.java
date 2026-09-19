package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通信分机展示视图对象 VO
 * <p>
 * 聚合 MySQL 静态配置与 Redis 在线注册状态 (fcc:extension:presence:{ext})。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通信分机展示视图")
public class ExtensionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分机主键 ID", example = "5001")
    private Long id;

    @Schema(description = "分机号码", example = "1001")
    private String extension;

    @Schema(description = "终端协议类型 (SIP, WEBRTC)", example = "SIP")
    private String endpointType;

    @Schema(description = "配置状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "实时在线状态 (ONLINE, OFFLINE)", example = "ONLINE")
    private String onlineStatus;

    @Schema(description = "SIP 注册 Contact", example = "sip:1001@192.168.1.100:5060")
    private String registeredContact;

    @Schema(description = "注册来源网络 IP", example = "192.168.1.100")
    private String registeredIp;

    @Schema(description = "当前绑定的坐席姓名", example = "钱丁君")
    private String boundAgentName;

    @Schema(description = "当前绑定的坐席工号", example = "901001")
    private String boundAgentWorkNo;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
