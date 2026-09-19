package com.chandler.fcc.common.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

/**
 * Go Sidecar 管理面 HTTP REST 统一响应实体
 * <p>
 * 封装 Go Sidecar :8088 接口返回的标准响应，包括状态码、消息及业务数据载荷。
 * </p>
 *
 * @author Chandler
 */
@Schema(description = "Go Sidecar 管理面统一响应体")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SidecarResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应状态码 (200 表示成功)
     */
    @Schema(description = "HTTP 状态码", example = "200")
    @Builder.Default
    private Integer code = 200;

    /**
     * 响应提示信息
     */
    @Schema(description = "响应消息描述", example = "success")
    @Builder.Default
    private String message = "success";

    /**
     * 业务数据载荷
     */
    @Schema(description = "业务数据内容")
    private T data;

    /**
     * 判断响应是否成功
     *
     * @return 若状态码为 200 则返回 true，否则返回 false
     */
    public boolean isSuccess() {
        return code != null && code == 200;
    }

    /**
     * 快捷构建成功应答
     *
     * @param data 响应数据载荷
     * @param <T>  载荷泛型类型
     * @return 成功状态的 SidecarResponse
     */
    public static <T> SidecarResponse<T> ok(T data) {
        return SidecarResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 快捷构建错误应答
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param <T>     载荷泛型类型
     * @return 错误状态的 SidecarResponse
     */
    public static <T> SidecarResponse<T> fail(Integer code, String message) {
        return SidecarResponse.<T>builder()
                .code(code != null ? code : 500)
                .message(message)
                .data(null)
                .build();
    }
}
