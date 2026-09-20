package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 呼入 DID 引示号持久化实体 (fcc_did_number)
 * <p>
 * 对应 MySQL 中 fcc_did_number 表，记录呼入接入号、关联中继及默认路由关键字。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_did_number")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class DidNumberEntity extends BaseEntity {

    /**
     * 归属中继线路 ID
     */
    private Long trunkId;

    /**
     * 呼入电话号码
     */
    private String phoneNumber;

    /**
     * 业务路由 Key (用于匹配 IVR 话务流程)
     */
    private String routeKey;

    /**
     * 状态 (ENABLED, DISABLED)
     */
    private String status;

    /**
     * 逻辑删除标记时间
     */
    private LocalDateTime deletedAt;
}
