package com.chandler.fcc.server.call;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 连续完整快照缺失屏障；未知快照中断计时，不能作为结束证据。 */
public class ChannelAbsenceTracker {
    private final Map<String, Long> absentSince = new HashMap<>();

    /** 检查双方话道是否持续缺失至少两分钟。
     * @param callId 通话标识
     * @param agent 坐席话道，可空
     * @param guest 客户话道
     * @param channels 完整快照集合，查询失败为空
     * @param now 本机单调计时毫秒
     * @return 是否允许按恢复未知结束通话
     */
    public boolean confirmed(String callId, String agent, String guest, Set<String> channels, long now) {
        if (channels == null || guest == null || channels.contains(guest)
                || (agent != null && channels.contains(agent))) {
            absentSince.remove(callId);
            return false;
        }
        Long first = absentSince.putIfAbsent(callId, now);
        return first != null && now - first >= 120_000;
    }

    /** 清理已结束会话的内存计时记录。
     * @param activeIds 仍存活的业务通话标识
     */
    public void retain(Set<String> activeIds) { absentSince.keySet().retainAll(activeIds); }
}
