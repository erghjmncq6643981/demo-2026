package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席信息修改更新请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席信息更新请求参数")
public class AgentUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "坐席主键ID不能为空")
    @Schema(description = "坐席雪花主键 ID", example = "10001")
    private Long id;

    @Schema(description = "坐席真实姓名", example = "张三")
    private String agentName;

    @Schema(description = "坐席手机号码", example = "13900139000")
    private String phoneNumber;

    @Schema(description = "坐席角色代码", example = "AGENT_MEMBER")
    private String roleCode;

    @Schema(description = "坐席状态 (ENABLED-启用, DISABLED-禁用)", example = "ENABLED")
    private String status;

    @Schema(description = "扩展元数据 (JSON 格式)", example = "{\"skillLevel\":5}")
    private String metadata;
}
