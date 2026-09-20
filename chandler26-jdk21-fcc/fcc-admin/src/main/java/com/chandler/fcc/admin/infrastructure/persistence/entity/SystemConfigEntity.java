package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 动态系统业务配置实体 (fcc_system_config)
 * <p>
 * 对应 MySQL 中 fcc_system_config 表，管理多层级作用域 (WEB, BACKEND, CLIENT, SYSTEM) 下的参数键值。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_system_config")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class SystemConfigEntity extends BaseEntity {

    /**
     * 配置参数名称 (Key)
     */
    private String propName;

    /**
     * 配置参数取值 (Value)
     */
    private String propValue;

    /**
     * 数据类型 (STRING, JSON, INT, BOOLEAN)
     */
    private String propType;

    /**
     * 作用域 (WEB, BACKEND, CLIENT, SYSTEM)
     */
    private String scope;

    /**
     * 配置中文描述与业务用途说明
     */
    private String description;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;
}
