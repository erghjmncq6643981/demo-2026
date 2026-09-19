package com.chandler.fcc.common.protocol;

/**
 * Sidecar 与 FCC 控制面之间的标准事件方法名。
 */
public final class FccEventMethods {

    /** 通道生命周期事件。 */
    public static final String CHANNEL = "Event.Channel";

    /** DTMF 输入事件。 */
    public static final String DTMF = "Event.DTMF";

    /** SIP 注册状态事件。 */
    public static final String REGISTRATION = "Event.Registration";

    /** 录音生命周期事件的标准方法名。 */
    public static final String RECORDING = "Event.Recording";

    private FccEventMethods() {
    }
}
