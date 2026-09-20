package com.chandler.fcc.server.event.domain;

import java.util.Arrays;
import lombok.Getter;

/**
 * 持久事件收件箱的处理状态。
 */
@Getter
public enum EventInboxStatus {

    PROCESSING("PROCESSING", "事件已领取并正在处理"),
    PROCESSED("PROCESSED", "事件已处理完成"),
    FAILED("FAILED", "事件处理失败，可在次数范围内重试"),
    UNKNOWN("UNKNOWN", "事件副作用不确定，需要人工或对账任务处理");

    private final String databaseValue;
    private final String desc;

    /**
     * 创建收件箱状态。
     *
     * @param databaseValue 数据库存储值
     * @param desc 中文业务说明
     */
    EventInboxStatus(String databaseValue, String desc) {
        this.databaseValue = databaseValue;
        this.desc = desc;
    }

    /**
     * 严格解析数据库中的状态值。
     *
     * @param databaseValue 数据库存储值
     * @return 对应收件箱状态
     * @throws IllegalArgumentException 状态为空或不在目录中
     */
    public static EventInboxStatus fromDatabaseValue(String databaseValue) {
        return Arrays.stream(values())
            .filter(status -> status.databaseValue.equals(databaseValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的事件收件箱状态: " + databaseValue));
    }
}
