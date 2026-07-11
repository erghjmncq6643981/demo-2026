package com.chandler.learning.agent.support;

import lombok.Data;

/**
 * 从数据库模型配置解析出的运行时连接信息。
 * <p>
 * 该对象只承载本次模型调用需要的连接参数，不从配置文件绑定模型供应商。
 */
@Data
public class AiModelConnectionConfig {

    /**
     * 模型配置是否启用。
     */
    private Boolean enabled;

    /**
     * 解密后的模型 API Key，仅在服务端内存中短暂使用。
     */
    private String apiKey;

    /**
     * 模型服务基础地址。
     */
    private String baseUrl;

    /**
     * Chat Completions 接口路径。
     */
    private String chatPath = LearningConstants.DEFAULT_CHAT_PATH;

    /**
     * 模型明细名称。
     */
    private String modelName;
}
