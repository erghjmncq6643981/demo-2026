package com.chandler.fcc.server.management.controller.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理服务新增或修改客户资料的内部请求参数。
 */
@Data
@Schema(description = "管理服务保存客户资料请求")
public class SaveCustomerReq {

    /** 客户姓名。 */
    @NotBlank(message = "客户姓名不能为空")
    @Size(max = 128, message = "客户姓名不能超过128个字符")
    @Schema(description = "客户姓名", example = "张三")
    private String name;

    /** 客户联系电话。 */
    @NotBlank(message = "客户联系电话不能为空")
    @Schema(description = "客户联系电话", example = "13800138000")
    private String phoneNumber;

    /** 客户单位。 */
    @Size(max = 128, message = "客户单位不能超过128个字符")
    @Schema(description = "客户单位", example = "示例科技有限公司")
    private String companyName;

    /** 客户备注。 */
    @Size(max = 2000, message = "客户备注不能超过2000个字符")
    @Schema(description = "客户备注", example = "重点客户，工作日下午联系")
    private String notes;

    /** 修改时使用的乐观锁版本。 */
    @Schema(description = "乐观锁版本，修改客户时必填", example = "0")
    private Long version;
}
