package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 账号创建/口令重置结果视图对象 VO
 * <p>
 * {@link #initialPassword} 仅在系统生成一次性随机口令时返回，且只返回这一次。
 * 前端必须在创建成功后立即提示运维人员记录，服务端不保存明文。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "账号创建或口令重置结果")
public class AccountCredentialVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账号主体类型 (CONSOLE / AGENT)", example = "AGENT")
    private String subjectType;

    @Schema(description = "账号主键 ID")
    private Long id;

    @Schema(description = "账号标识 (控制台账号为 username，坐席为工号)", example = "901001")
    private String account;

    @Schema(description = "账户显示姓名", example = "陈松")
    private String displayName;

    @Schema(description = "系统生成的一次性初始口令；调用方显式指定口令时为 null")
    private String initialPassword;

    @Schema(description = "分机/SIP 注册口令 (仅坐席创建场景返回)，用于配置实体话机或 WebRTC 软话机")
    private String extensionSecret;

    @Schema(description = "提示信息", example = "请立即登录并修改初始口令")
    private String hint;
}
