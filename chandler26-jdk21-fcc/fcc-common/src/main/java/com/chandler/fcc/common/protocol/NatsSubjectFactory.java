package com.chandler.fcc.common.protocol;

/**
 * 集中构造 FCC 与 Sidecar 之间的 NATS 主题。
 */
public final class NatsSubjectFactory {

    private NatsSubjectFactory() {
    }

    /**
     * 构造节点指令主题。
     *
     * @param nodeId Sidecar 节点标识
     * @return {@code fs.cmd.{nodeId}}
     */
    public static String command(String nodeId) {
        return "fs.cmd." + segment(nodeId, "节点标识");
    }

    /**
     * 构造指定事件方法的主题。
     *
     * @param nodeId Sidecar 节点标识
     * @param method 规范事件方法
     * @return {@code fs.event.{nodeId}.{category}}
     */
    public static String event(String nodeId, FccEventMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("事件方法不能为空");
        }
        return "fs.event." + segment(nodeId, "节点标识") + "." + method.getCategory();
    }

    /**
     * 构造某节点的事件主题前缀，用于校验消息来源。
     *
     * @param nodeId Sidecar 节点标识
     * @return 以点结尾的事件主题前缀
     */
    public static String eventPrefix(String nodeId) {
        return "fs.event." + segment(nodeId, "节点标识") + ".";
    }

    /**
     * 返回全部 FCC 事件主题通配符。
     *
     * @return JetStream 消费主题
     */
    public static String allEvents() {
        return "fs.event.>";
    }

    /**
     * 构造节点心跳主题。
     *
     * @param nodeId Sidecar 节点标识
     * @return {@code fs.status.{nodeId}.heartbeat}
     */
    public static String heartbeat(String nodeId) {
        return "fs.status." + segment(nodeId, "节点标识") + ".heartbeat";
    }

    /**
     * 校验 NATS 单段主题值，防止主题越界或通配符注入。
     *
     * @param value 待校验值
     * @param label 字段中文名称
     * @return 去除首尾空白的单段值
     */
    private static String segment(String value, String label) {
        if (value == null || !value.trim().matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException(label + "不合法");
        }
        return value.trim();
    }
}
