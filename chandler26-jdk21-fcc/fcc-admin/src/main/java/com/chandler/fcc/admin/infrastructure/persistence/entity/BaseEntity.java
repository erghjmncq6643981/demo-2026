package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 管理域持久化实体的通用审计基类。
 *
 * <p>仅适用于同时具备雪花主键、创建时间和更新时间的业务表。追加型事实表不应为了继承本类而
 * 虚构更新时间字段。</p>
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 雪花主键。
     */
    @TableId
    private Long id;

    /**
     * 记录创建时间，统一使用 UTC。
     */
    private LocalDateTime createdAt;

    /**
     * 记录最近更新时间，统一使用 UTC。
     */
    private LocalDateTime updatedAt;
}
