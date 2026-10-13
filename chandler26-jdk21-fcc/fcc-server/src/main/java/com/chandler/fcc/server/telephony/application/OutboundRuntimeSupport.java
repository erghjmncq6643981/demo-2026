package com.chandler.fcc.server.telephony.application;

/** 外呼固定模板与终态键的唯一来源，不承载基础设施或业务副作用。 */
final class OutboundRuntimeSupport {
    static final String TEMPLATE_AGENT_FIRST = "AGENT_FIRST";
    static final String TEMPLATE_AGENT_ORIGINATED = "AGENT_ORIGINATED";
    static final String TEMPLATE_NOTIFICATION = "NOTIFICATION";
    static final String FLOW_SYSTEM_NOTIFICATION = "SYSTEM_NOTIFICATION";
    static final String DATA_TERMINAL = "terminal";


    /** 禁止构造无状态模板工具。 */
    private OutboundRuntimeSupport() {}

    /**
     * 判断模板是否需要坐席服务参与。
     * @param template 固定模板
     * @return 是否为人工外呼
     */
    static boolean isAgentCall(String template) {
        return TEMPLATE_AGENT_FIRST.equals(template) || TEMPLATE_AGENT_ORIGINATED.equals(template);
    }
}
