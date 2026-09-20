package com.chandler.fcc.server.call;

/** 话道生命周期单调策略；迟到事件可补事实，但不能撤销接听、桥接或结束。 */
public final class LegStatePolicy {
    /** 工具类不允许实例化。 */
    private LegStatePolicy() {}

    /** 判断新状态能否替换现态。
     * @param current 当前状态
     * @param incoming 到达状态
     * @return 已知且不倒退时为 true；未知状态不覆盖已知状态
     */
    public static boolean accepts(String current, String incoming) {
        int next = rank(incoming);
        return next >= 0 && next >= rank(current);
    }

    /** 获取生命周期阶段序号。
     * @param state 协议状态
     * @return 阶段序号，未知值返回 -1
     */
    private static int rank(String state) {
        if (state == null) return -1;
        return switch (state) {
            case "START" -> 0;
            case "RINGING" -> 1;
            case "ANSWERED", "READY" -> 2;
            case "BRIDGE" -> 3;
            case "HANGUP" -> 4;
            case "DESTROY" -> 5;
            default -> -1;
        };
    }
}
