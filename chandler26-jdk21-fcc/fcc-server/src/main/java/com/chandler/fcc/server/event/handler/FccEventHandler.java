package com.chandler.fcc.server.event.handler;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 处理一种 Sidecar 标准事件的应用端口。
 */
public interface FccEventHandler {

    /**
     * 判断处理器是否负责指定事件方法。
     *
     * @param method 标准事件方法名
     * @return 负责该事件时返回 {@code true}
     */
    boolean supports(String method);

    /**
     * 处理已经完成来源身份校验的事件参数。
     *
     * @param params 标准事件参数
     */
    void handle(JsonNode params);
}
