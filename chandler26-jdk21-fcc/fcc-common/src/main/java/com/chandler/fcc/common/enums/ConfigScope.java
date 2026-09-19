package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 动态业务配置作用域枚举
 * <p>
 * 对应 fcc_system_config 表的 scope 字段，用于多级参数隔离。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum ConfigScope {

    /**
     * 前端 UI 行为配置（如话后整理倒计时、防撞单报警回溯天数等）
     */
    WEB("前端UI作用域"),

    /**
     * 控制面调度逻辑配置（排队超时阈值、振铃重试次数、黑名单拦截等）
     */
    BACKEND("控制面调度作用域"),

    /**
     * 坐席 PC 客户端配置（网络心跳周期、SIP 软电话本地缓冲区等）
     */
    CLIENT("PC客户端作用域"),

    /**
     * 系统底座基础参数（NAS 录音存储根路径、默认 TTS 发音人参数等）
     */
    SYSTEM("系统底座作用域");

    /**
     * 配置作用域中文描述
     */
    private final String desc;

    /**
     * 构造配置作用域枚举
     *
     * @param desc 中文描述
     */
    ConfigScope(String desc) {
        this.desc = desc;
    }

    /**
     * 根据字符串解析配置作用域
     *
     * @param name 字符串标识
     * @return 匹配的 ConfigScope，默认返回 BACKEND
     */
    public static ConfigScope from(String name) {
        if (name == null || name.trim().isEmpty()) {
            return BACKEND;
        }
        for (ConfigScope scope : ConfigScope.values()) {
            if (scope.name().equalsIgnoreCase(name)) {
                return scope;
            }
        }
        return BACKEND;
    }
}
