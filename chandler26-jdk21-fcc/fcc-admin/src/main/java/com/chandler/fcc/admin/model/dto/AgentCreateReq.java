package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席创建请求 DTO
 * <p>
 * 坐席账号与档案全部落在 fcc_agent 表。口令留空时由服务端生成一次性随机口令并在响应中返回。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席创建请求参数")
public class AgentCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "坐席工号不能为空")
    @Size(max = 64, message = "坐席工号长度不能超过 64")
    @Schema(description = "坐席工号 (唯一，同时作为登录账号)", example = "10001")
    private String workNo;

    @NotBlank(message = "坐席姓名不能为空")
    @Size(max = 128, message = "坐席姓名长度不能超过 128")
    @Schema(description = "坐席真实姓名", example = "张三")
    private String agentName;

    @Size(max = 32, message = "手机号码长度不能超过 32")
    @Schema(description = "坐席手机联系电话", example = "13800138000")
    private String phoneNumber;

    @Size(max = 32, message = "SIP 分机号长度不能超过 32")
    @Schema(description = "工位 SIP 话机分机号；留空则不登记 SIP 接听终端", example = "1001")
    private String sipExtension;

    @Schema(description = "坐席角色代码 (AGENT_ADMIN 班长, SUPERVISOR 值班长, AGENT_MEMBER 普通坐席)；缺省为 AGENT_MEMBER",
            example = "AGENT_MEMBER")
    private String roleCode;

    @Size(min = 8, max = 64, message = "登录口令长度需在 8 到 64 之间")
    @Schema(description = "坐席登录口令；留空则由系统生成一次性随机口令并在响应中返回")
    private String password;

    @Schema(description = "扩展元数据 (JSON 格式)", example = "{\"department\":\"技术运营\"}")
    private String metadata;
}
