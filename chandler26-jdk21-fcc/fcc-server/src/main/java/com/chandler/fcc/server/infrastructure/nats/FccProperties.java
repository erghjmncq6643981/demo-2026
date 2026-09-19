package com.chandler.fcc.server.infrastructure.nats;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FCC 核心配置属性类
 * <p>
 * 读取并管理 NATS 消息总线连接地址、默认软交换节点、指令超时及 Sidecar 管理面地址等参数。
 * </p>
 *
 * @author Chandler
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fcc")
public class FccProperties {

    /**
     * NATS 消息集群连接 URL 地址
     */
    private String natsUrl = "nats://127.0.0.1:4222";

    /**
     * 默认软交换与 Sidecar 节点标识符 (node_id)
     */
    private String defaultNodeId;

    /**
     * 控制流程标识符默认前缀
     */
    private String ctrlIdPrefix = "fcc-ctrl";

    /**
     * JSON-RPC 同步调用超时时间（毫秒）
     */
    private long rpcTimeoutMillis = 5000;

    /**
     * Go Sidecar 管理面 HTTP 服务根地址
     */
    private String sidecarAdminUrl = "http://127.0.0.1:8088";
}
