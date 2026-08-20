package com.chandler.learning.agent.common.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据对象通用审计字段。
 * <p>
 * 所有 DO 统一继承该基类，保证创建人、更新人、逻辑删除、乐观锁和时间字段在工程内保持一致。
 */
@Data
public abstract class BaseEntity {

    /**
     * 创建人用户 ID；系统初始化或匿名上下文使用 0。
     */
    @Schema(description = "创建人用户 ID；系统初始化或匿名上下文使用 0")
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 创建时间。
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人用户 ID；系统初始化或匿名上下文使用 0。
     */
    @Schema(description = "更新人用户 ID；系统初始化或匿名上下文使用 0")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 更新时间。
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：false-正常，true-已删除。
     */
    @Schema(description = "逻辑删除标记：false-正常，true-已删除")
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

    /**
     * 乐观锁版本号。
     */
    @Schema(description = "乐观锁版本号")
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;
}
