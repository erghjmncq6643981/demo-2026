package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通信中继线路与运营商网关实体 (fcc_telephony_trunk)
 * <p>
 * 对应 MySQL 中 fcc_telephony_trunk 表，维护 SIP 中继代码、网关名称、呼叫方向与并发限制。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_telephony_trunk")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class TelephonyTrunkEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 中继雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 中继唯一编码
     */
    private String trunkCode;

    /**
     * 中继名称
     */
    private String trunkName;

    /**
     * 运营商代码 (如 CUCC, CMCC, CTCC)
     */
    private String carrierCode;

    /**
     * FreeSWITCH Sofia 网关名称
     */
    private String gatewayName;

    /**
     * 呼叫方向 (INBOUND, OUTBOUND, BOTH)
     */
    private String direction;

    /**
     * 中继状态 (ENABLED, DISABLED)
     */
    private String status;

    /**
     * 最大允许并发数 (0 为无限制)
     */
    private Integer maxConcurrent;

    /**
     * 网关配置扩展 (JSON)
     */
    private String configJson;

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
