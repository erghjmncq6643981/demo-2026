package com.chandler.fcc.common.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * 分机开户与更新请求传输对象
 * <p>
 * 用于向 Go Sidecar 管理面 HTTP REST (:8088/api/v1/extensions) 提交分机配置写入与热重载请求。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "分机开户与更新请求对象")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ExtensionCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SIP 分机号码
     */
    @Schema(description = "SIP 分机号码", example = "1088", requiredMode = Schema.RequiredMode.REQUIRED)
    private String extension;

    /**
     * SIP 注册认证密码
     */
    @Schema(description = "SIP 注册认证密码", example = "PassWord@123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    /**
     * 拨号计划呼叫路由上下文
     */
    @Schema(description = "拨号计划呼叫路由上下文", example = "default")
    @Builder.Default
    private String context = "default";

    /**
     * 呼叫组成员标识
     */
    @Schema(description = "呼叫所属组标识", example = "default")
    @Builder.Default
    private String callgroup = "default";
}
