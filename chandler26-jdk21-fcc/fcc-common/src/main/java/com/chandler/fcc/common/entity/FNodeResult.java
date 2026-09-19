package com.chandler.fcc.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

/**
 * FNode JSON-RPC 2.0 响应结果模型
 * <p>
 * 封装由 Go Sidecar / FreeSWITCH 返回的 FNode.* 控制指令同步应答数据。
 * </p>
 *
 * @author Chandler
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FNodeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应节点标识符
     */
    @JsonProperty("node_id")
    private String nodeId;

    /**
     * 业务响应状态码 (200 为成功，202 为已接收处理中)
     */
    @Builder.Default
    private Integer code = 200;

    /**
     * 业务响应提示信息
     */
    @Builder.Default
    private String message = "OK";

    /**
     * 关联的 FreeSWITCH 通道 UUID
     */
    private String uuid;

    /**
     * 关联的控制流程标识
     */
    @JsonProperty("ctrl_uuid")
    private String ctrlUuid;

    /**
     * 挂机原因或错误原因
     */
    private String cause;

    /**
     * 收号或按键值（针对 ReadDTMF 指令）
     */
    private String dtmf;

    /**
     * 当前话道或节点状态
     */
    private String state;

    /**
     * 节点当前活跃并发通道数
     */
    @JsonProperty("active_channels")
    private Long activeChannels;

    /**
     * 节点最大支持并发通道数
     */
    @JsonProperty("max_channels")
    private Integer maxChannels;

    /**
     * 节点运行时长（秒）
     */
    @JsonProperty("uptime_seconds")
    private Long uptimeSeconds;

    /**
     * 扩展载荷对象
     */
    private Object data;

    /**
     * 节点响应时间戳
     */
    private Long timestamp;

    /**
     * 判定当前指令是否执行成功
     *
     * @return 若状态码为 200 或 202 则返回 true，否则返回 false
     */
    public boolean isSuccess() {
        return code != null && (code == 200 || code == 202);
    }
}
