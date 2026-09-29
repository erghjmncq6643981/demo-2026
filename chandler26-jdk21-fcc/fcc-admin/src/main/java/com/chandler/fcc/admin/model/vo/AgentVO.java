package com.chandler.fcc.admin.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席信息视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席详情展示视图")
public class AgentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "坐席主键 ID", example = "1001")
    private Long id;

    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    @Schema(description = "坐席真实姓名", example = "钱丁君")
    private String agentName;

    @Schema(description = "手机号码", example = "13800138000")
    private String phoneNumber;

    @Schema(description = "角色代码 (AGENT_ADMIN / SUPERVISOR / AGENT_MEMBER)", example = "AGENT_MEMBER")
    private String roleCode;

    @Schema(description = "角色中文名称", example = "班长主管")
    private String roleName;

    @Schema(description = "状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "登录状态 (ONLINE / OFFLINE)", example = "ONLINE")
    private String loginStatus;

    @Schema(description = "登录状态中文", example = "在线")
    private String loginStatusDesc;

    @Schema(description = "通话状态代码", example = "IDLE")
    private String callStatus;

    @Schema(description = "通话状态中文", example = "空闲")
    private String callStatusDesc;

    @Schema(description = "是否已登录", example = "true")
    private Boolean isLoggedIn;

    @Schema(description = "是否为主管/班长席 (由角色代码推导)", example = "false")
    private Boolean isSupervisor;

    @Schema(description = "是否已设置登录口令", example = "true")
    private Boolean passwordConfigured;

    @Schema(description = "最近一次成功登录时间")
    private LocalDateTime lastLoginAt;

    @Schema(description = "当前绑定的分机号", example = "1001")
    private String currentExtension;

    @Schema(description = "当前接听终端类型", example = "WEBRTC")
    private String boundEndpointType;

    @Schema(description = "扩展元数据 (JSON)", example = "{}")
    private String metadata;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
