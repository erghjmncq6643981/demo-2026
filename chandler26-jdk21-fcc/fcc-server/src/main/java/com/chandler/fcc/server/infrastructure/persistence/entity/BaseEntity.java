package com.chandler.fcc.server.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 呼叫运行域持久化实体的通用审计基类。
 *
 * <p>仅用于同时具有雪花主键、创建时间和更新时间的可变事实。事件等追加型事实继续使用自身
 * 的时间语义，避免把数据库模型抽象成并不存在的统一形态。</p>
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
