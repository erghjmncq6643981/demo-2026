package com.chandler.fcc.admin.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理台统一 REST API 响应封装
 *
 * @param <T> 数据载荷泛型
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一通用响应报文")
public class CommonResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "业务响应码，200 表示成功", example = "200")
    private int code;

    @Schema(description = "响应提示信息", example = "操作成功")
    private String message;

    @Schema(description = "业务结果数据实体")
    private T data;

    @Schema(description = "响应时间戳 (毫秒)", example = "1726640000000")
    private long timestamp;

    /**
     * 构造成功响应
     *
     * @param data 返回的业务数据
     * @param <T>  数据类型
     * @return 成功响应结果实体
     */
    public static <T> CommonResult<T> success(T data) {
        return CommonResult.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构造成功无数据响应
     *
     * @param <T> 数据类型
     * @return 成功响应结果实体
     */
    public static <T> CommonResult<T> success() {
        return success(null);
    }

    /**
     * 构造失败错误响应
     *
     * @param code    错误状态码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 错误响应结果实体
     */
    public static <T> CommonResult<T> error(int code, String message) {
        return CommonResult.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构造通用 500 失败响应
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return 错误响应结果实体
     */
    public static <T> CommonResult<T> failed(String message) {
        return error(500, message);
    }
}
