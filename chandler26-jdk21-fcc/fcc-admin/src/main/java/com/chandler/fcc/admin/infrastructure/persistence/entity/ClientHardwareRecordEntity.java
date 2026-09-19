package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席客户端硬件指纹安全审计实体 (fcc_client_hardware_record)
 * <p>
 * 对应 MySQL 中 fcc_client_hardware_record 表，记录坐席登录时上报的网卡 MAC、磁盘序列号、CPU/BIOS 硬件特征。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_client_hardware_record")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ClientHardwareRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 硬件审计记录雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 坐席主键 ID
     */
    private Long agentId;

    /**
     * 坐席工号
     */
    private String workNum;

    /**
     * 客户端软件版本号
     */
    private String clientVersion;

    /**
     * 网卡物理 MAC 地址
     */
    private String macAddr;

    /**
     * 硬盘序列号
     */
    private String diskSeq;

    /**
     * CPU 序列号
     */
    private String cpuSeq;

    /**
     * 主板 BIOS 序列号
     */
    private String biosSeq;

    /**
     * 客户端操作系统类型及版本
     */
    private String os;

    /**
     * 登录来源网络 IP 地址
     */
    private String ipAddr;

    /**
     * 登录发生时间
     */
    private LocalDateTime loginTime;

    /**
     * 记录创建入库时间
     */
    private LocalDateTime createdAt;
}
