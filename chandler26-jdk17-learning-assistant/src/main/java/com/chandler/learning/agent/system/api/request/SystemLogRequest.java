package com.chandler.learning.agent.system.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.chandler.learning.agent.system.domain.constant.SystemLogConstants;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SystemLogRequest 类。
 */
@Data
public class SystemLogRequest {

    /** 日志类型。 */
    @Size(max = SystemLogConstants.MAX_TYPE_LENGTH)
    @Schema(description = "业务类型")
    private String type;

    /** 业务人员可读标题。 */
    @Size(max = SystemLogConstants.MAX_TITLE_LENGTH)
    @Schema(description = "标题")
    private String title;

    /** 日志详情；服务端会限制长度且不将敏感数据写入系统日志。 */
    @Size(max = SystemLogConstants.MAX_DETAIL_LENGTH)
    @Schema(description = "详情")
    private String detail;

    /** 客户端来源标识；服务端入库时固定为 client。 */
    @Size(max = SystemLogConstants.MAX_SOURCE_LENGTH)
    @Schema(description = "来源")
    private String source;

    /** 可选关联业务类型。 */
    @Size(max = SystemLogConstants.MAX_BUSINESS_TYPE_LENGTH)
    @Schema(description = "业务类型")
    private String businessType;

    /** 可选关联业务 ID。 */
    @Size(max = SystemLogConstants.MAX_BUSINESS_ID_LENGTH)
    @Schema(description = "业务标识")
    private String businessId;
}
