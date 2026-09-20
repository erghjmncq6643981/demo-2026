package com.chandler.fcc.server.customer.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 客户详情和写入模型，服务端覆盖身份与版本外的归属字段。 */
@Data
@Schema(description = "坐席有权访问的客户资料")
public class CustomerRecord {
    @Schema(description = "客户标识") private String id;
    @Schema(description = "客户姓名") private String name;
    @Schema(description = "规范化电话号码") private String phoneNumber;
    @Schema(description = "客户单位") private String companyName;
    @Schema(description = "客户备注，仅详情接口返回") private String notes;
    @Schema(description = "乐观锁版本，修改时必填") private Long version;
}
