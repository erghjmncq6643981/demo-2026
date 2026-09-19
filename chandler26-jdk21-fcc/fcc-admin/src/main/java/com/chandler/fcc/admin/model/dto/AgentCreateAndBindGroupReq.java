package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建新坐席并直接加入技能组请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建新坐席并绑定技能组请求")
public class AgentCreateAndBindGroupReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "坐席工号不能为空")
    @Schema(description = "坐席工号 (唯一，同时作为登录账号)", example = "10005")
    private String workNo;

    @NotBlank(message = "坐席姓名不能为空")
    @Schema(description = "坐席真实姓名", example = "王五")
    private String agentName;

    @Schema(description = "联系电话")
    private String phoneNumber;

    @Schema(description = "工位 SIP 话机分机号；留空则不登记 SIP 接听终端", example = "1005")
    private String sipExtension;

    @Schema(description = "坐席角色编码 (AGENT_ADMIN / SUPERVISOR / AGENT_MEMBER)", example = "AGENT_MEMBER")
    private String roleCode;

    @Schema(description = "登录口令；留空则由系统生成一次性随机口令并在响应中返回")
    private String password;

    @Schema(description = "组内角色 (LEADER, MEMBER)", example = "MEMBER")
    private String memberRole;

    @Schema(description = "分发优先级", example = "0")
    private Integer priority;
}
