package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外呼主叫号码池实体 (fcc_outbound_number)
 * <p>
 * 对应 MySQL 中 fcc_outbound_number 表，支持按号池编码分组外呼、号码可用性与频次流控。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_outbound_number")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class OutboundNumberEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 外呼主叫号码雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 绑定的出局中继线路 ID
     */
    private Long trunkId;

    /**
     * 外呼展示号码
     */
    private String phoneNumber;

    /**
     * 号码池代码 (如 default, vip, telemarketing)
     */
    private String poolCode;

    /**
     * 状态 (AVAILABLE-可用, RESTING-冷却静默, SUSPENDED-熔断封禁)
     */
    private String status;

    /**
     * 单号码并发上限
     */
    private Integer maxConcurrent;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记时间
     */
    private LocalDateTime deletedAt;
}
