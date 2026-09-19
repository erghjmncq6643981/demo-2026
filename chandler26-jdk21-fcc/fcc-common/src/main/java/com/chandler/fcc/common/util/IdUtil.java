package com.chandler.fcc.common.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式全局唯一 ID 生成工具类
 * <p>
 * 提供高性能 Snowflake（雪花算法）BIGINT ID 生成能力以及通话控制领域专属的业务标识（call_id、ctrl_id、command_id）。
 * 保证在分布式控制面集群中的单调递增性与无冲突性。
 * </p>
 *
 * @author Chandler
 */
public final class IdUtil {

    /**
     * 起始时间戳 (2026-01-01 00:00:00 UTC = 1767225600000L)
     */
    private static final long START_EPOCH = 1767225600000L;

    /**
     * 机器标识所占的位数 (5位，最多支持 32 个节点)
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 数据中心标识所占的位数 (5位，最多支持 32 个机房)
     */
    private static final long DATACENTER_ID_BITS = 5L;

    /**
     * 毫秒内序列所占的位数 (12位，每毫秒最多 4096 个序列号)
     */
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final SnowflakeGenerator DEFAULT_SNOWFLAKE = new SnowflakeGenerator(1L, 1L);

    private IdUtil() {
    }

    /**
     * 生成 64 位雪花算法全局唯一数值 ID
     *
     * @return 64位无符号/正数 Long ID
     */
    public static long nextId() {
        return DEFAULT_SNOWFLAKE.nextId();
    }

    /**
     * 生成雪花算法字符串 ID
     *
     * @return 字符串形式的 Long ID
     */
    public static String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 生成标准 32 位无连字符 UUID
     *
     * @return 32 位十六进制字符串
     */
    public static String getUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成全局通话业务唯一标识 (call_id)
     * <p>
     * 格式: call-{snowflakeId}，全通话周期保持稳定不变。
     * </p>
     *
     * @return 业务通话唯一标识
     */
    public static String getCallId() {
        return "call-" + nextIdStr();
    }

    /**
     * 生成控制流程关联唯一标识 (ctrl_id)
     * <p>
     * 格式: {prefix}-{timestamp}-{uuid8}
     * </p>
     *
     * @param prefix 流程前缀（如 "fcc-inbound" 或 "fcc-outbound"）
     * @return 控制流程关联标识
     */
    public static String getCtrlId(String prefix) {
        String safePrefix = (prefix == null || prefix.trim().isEmpty()) ? "fcc" : prefix;
        return safePrefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成 FNode 指令唯一编号 (command_id)
     *
     * @return 格式为 cmd-{snowflakeId} 的指令 ID
     */
    public static String getCommandId() {
        return "cmd-" + nextIdStr();
    }

    /**
     * 生成事件唯一编号 (event_id)
     *
     * @return 格式为 evt-{snowflakeId} 的事件 ID
     */
    public static String getEventId() {
        return "evt-" + nextIdStr();
    }

    /**
     * 内部雪花算法生成器实体
     */
    private static class SnowflakeGenerator {
        private final long workerId;
        private final long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        SnowflakeGenerator(long workerId, long datacenterId) {
            if (workerId > MAX_WORKER_ID || workerId < 0) {
                throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
            }
            if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
                throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", MAX_DATACENTER_ID));
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        synchronized long nextId() {
            long timestamp = timeGen();
            if (timestamp < lastTimestamp) {
                // 时钟回拨保护（回拨小于 5ms 则自旋等待）
                long offset = lastTimestamp - timestamp;
                if (offset <= 5) {
                    try {
                        wait(offset << 1);
                        timestamp = timeGen();
                        if (timestamp < lastTimestamp) {
                            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new RuntimeException(String.format("Clock moved backwards by %dms. Refusing to generate id", offset));
                }
            }

            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            return ((timestamp - START_EPOCH) << TIMESTAMP_LEFT_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }

        private long tilNextMillis(long lastTimestamp) {
            long timestamp = timeGen();
            while (timestamp <= lastTimestamp) {
                timestamp = timeGen();
            }
            return timestamp;
        }

        private long timeGen() {
            return System.currentTimeMillis();
        }
    }
}
