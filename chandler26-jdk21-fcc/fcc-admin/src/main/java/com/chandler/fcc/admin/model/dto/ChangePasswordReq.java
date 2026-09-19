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
 * 当前登录用户自助修改口令请求 DTO
 * <p>
 * 需同时提供原口令以确认身份，适用于超管与坐席的所有登录态。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "自助修改登录口令请求参数")
public class ChangePasswordReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "原口令不能为空")
    @Schema(description = "当前使用的原口令")
    private String oldPassword;

    @NotBlank(message = "新口令不能为空")
    @Size(min = 8, max = 64, message = "新口令长度需在 8 到 64 之间")
    @Schema(description = "新口令", example = "Ops@2026#Two")
    private String newPassword;
}
