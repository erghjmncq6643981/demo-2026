package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 漏话回拨任务分页查询入参
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "漏话回拨查询入参")
public class CallbackTaskQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页码", defaultValue = "1")
    @Builder.Default
    private Integer pageNum = 1;

    @Schema(description = "每页数量", defaultValue = "10")
    @Builder.Default
    private Integer pageSize = 10;

    @Schema(description = "任务状态 (PENDING, ASSIGNED, CALLED, COMPLETED, CANCELLED)")
    private String status;

    @Schema(description = "客户手机号模糊匹配")
    private String customerNumber;
}
