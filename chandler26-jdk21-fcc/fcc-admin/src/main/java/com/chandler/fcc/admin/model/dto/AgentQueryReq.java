package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席多条件分页查询请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席分页检索请求参数")
public class AgentQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "坐席工号", example = "901001")
    private String workNo;

    @Schema(description = "坐席姓名 (模糊检索)", example = "钱丁君")
    private String agentName;

    @Schema(description = "手机号码", example = "138")
    private String phoneNumber;

    @Schema(description = "角色标识", example = "AGENT_ADMIN")
    private String roleCode;

    @Schema(description = "坐席状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "页码 (默认 1)", example = "1")
    @Builder.Default
    private long pageNum = 1;

    @Schema(description = "每页数量 (默认 20)", example = "20")
    @Builder.Default
    private long pageSize = 20;
}
