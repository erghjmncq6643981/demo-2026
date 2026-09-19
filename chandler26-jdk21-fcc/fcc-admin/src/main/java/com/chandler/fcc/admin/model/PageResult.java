package com.chandler.fcc.admin.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 管理台通用分页数据结果容器
 *
 * @param <T> 数据记录泛型
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一分页查询结果容器")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码", example = "1")
    private long pageNum;

    @Schema(description = "每页大小", example = "20")
    private long pageSize;

    @Schema(description = "符合条件的总记录数", example = "100")
    private long total;

    @Schema(description = "当前页结果数据列表")
    private List<T> list;

    /**
     * 构造空分页结果
     *
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      记录泛型
     * @return 空分页结果对象
     */
    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return PageResult.<T>builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(0L)
                .list(Collections.emptyList())
                .build();
    }
}
