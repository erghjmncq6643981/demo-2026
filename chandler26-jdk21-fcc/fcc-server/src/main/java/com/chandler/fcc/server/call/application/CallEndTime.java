package com.chandler.fcc.server.call.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 结束事件时间解析，优先保留 FreeSWITCH end_epoch，缺失时使用源事件时间。 */
public final class CallEndTime {
    /** 禁止实例化无状态时间解析工具。 */
    private CallEndTime() {}

    /**
     * 读取结束时间，不使用服务器每日对账时间替代已有源结束时间。
     * @param params Channel 结束事件，可为空
     * @return UTC epoch 毫秒
     */
    public static long epochMillis(JsonNode params) {
        if (params != null) {
            long endSeconds = params.path("end_epoch").asLong();
            if (endSeconds > 0) return Math.multiplyExact(endSeconds, 1000);
            long source = params.path("timestamp").asLong();
            if (source > 0) return source;
        }
        return Instant.now().toEpochMilli();
    }
}
