package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 通信分机持久化实体 (fcc_extension)
 * <p>
 * 对应 MySQL 中 fcc_extension 表，记录 SIP/WebRTC 分机号码、终端类型及密文凭据。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_extension")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@SuperBuilder
public class ExtensionEntity extends BaseEntity {

    /**
     * 分机号码 (如 1001)
     */
    private String extension;

    /**
     * 终端类型 (SIP, WEBRTC)
     */
    private String endpointType;

    /**
     * 分机注册鉴权密码密文
     */
    private byte[] credentialSecret;

    /**
     * 状态 (ENABLED, DISABLED)
     */
    private String status;

    /**
     * 当前绑定的坐席工号 (实体话机 0000 语音自助绑定或后台指定)
     */
    private String agentWorkNo;

    /**
     * 当前绑定的坐席姓名
     */
    private String agentName;

    /**
     * 逻辑删除标记时间
     */
    private LocalDateTime deletedAt;
}
