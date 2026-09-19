package com.chandler.fcc.admin.config;

import com.chandler.fcc.common.json.FccIdentifierJacksonModule;
import com.fasterxml.jackson.databind.Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FCC 管理服务 JSON 序列化配置。
 */
@Configuration
public class FccJacksonConfig {

    /**
     * 注册前端安全的 Long 标识符序列化规则。
     *
     * @return FCC 标识符 Jackson 模块
     */
    @Bean
    public Module fccIdentifierJacksonModule() {
        return FccIdentifierJacksonModule.create();
    }
}
