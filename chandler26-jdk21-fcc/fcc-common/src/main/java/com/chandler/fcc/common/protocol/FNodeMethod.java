package com.chandler.fcc.common.protocol;

import java.util.Arrays;
import lombok.Getter;

/**
 * Java 控制面与 Sidecar 之间唯一允许使用的 FNode JSON-RPC 方法目录。
 */
@Getter
public enum FNodeMethod {

    DIAL("FNode.Dial", "发起呼叫"),
    ANSWER("FNode.Answer", "应答指定话道"),
    CHANNEL_BRIDGE("FNode.ChannelBridge", "桥接两路话道"),
    READ_DTMF("FNode.ReadDTMF", "播放提示并收取按键"),
    PLAY("FNode.Play", "在话道中播放媒体"),
    RECORD("FNode.Record", "开始或停止话道录音"),
    HANGUP("FNode.Hangup", "挂断指定话道"),
    TRANSFER("FNode.Transfer", "转接指定话道"),
    NATIVE_API("FNode.NativeAPI", "执行受限原生指令"),
    DRAIN("FNode.Drain", "将节点切换为排空状态"),
    RESUME("FNode.Resume", "恢复节点接收新通话"),
    STATUS("FNode.Status", "查询节点运行状态"),
    CHANNEL_SNAPSHOT("FNode.ChannelSnapshot", "查询节点完整话道快照"),
    COMMAND_RESULT("FNode.CommandResult", "查询既有命令结果");

    private final String wireName;
    private final String desc;

    /**
     * 创建 FNode 方法定义。
     *
     * @param wireName JSON-RPC 报文方法名
     * @param desc 中文业务说明
     */
    FNodeMethod(String wireName, String desc) {
        this.wireName = wireName;
        this.desc = desc;
    }

    /**
     * 按 wire 方法名严格解析规范方法。
     *
     * @param wireName JSON-RPC 方法名
     * @return 对应方法枚举
     * @throws IllegalArgumentException 方法不在规范目录中
     */
    public static FNodeMethod fromWireName(String wireName) {
        return Arrays.stream(values())
            .filter(method -> method.wireName.equals(wireName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的 FNode 方法: " + wireName));
    }
}
