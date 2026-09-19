package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理员重置指定账号口令请求 DTO
 * <p>
 * 口令留空时由系统生成一次性随机口令并在响应中返回，避免使用任何统一默认口令。
 * </p>
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重置指定账号口令请求参数")
public class ResetPasswordReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(min = 8, max = 64, message = "口令长度需在 8 到 64 之间")
    @Schema(description = "新口令；留空则生成一次性随机口令", example = "Ops@2026#Reset")
    private String password;
}
