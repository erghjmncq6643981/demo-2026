package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 漏话回拨任务派单入参
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "漏话派单入参")
public class CallbackTaskAssignReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "指派坐席工号", example = "901415")
    private String agentWorkNo;

    @Schema(description = "指派坐席姓名", example = "舒欣", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指派坐席姓名不能为空")
    private String agentName;
}
