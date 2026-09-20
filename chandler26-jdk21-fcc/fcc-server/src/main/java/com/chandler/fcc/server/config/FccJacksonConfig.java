package com.chandler.fcc.server.config;

import com.chandler.fcc.common.json.FccIdentifierJacksonModule;
import com.fasterxml.jackson.databind.Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * FCC 核心通信服务 JSON 序列化配置。
 */
@Configuration
public class FccJacksonConfig implements WebMvcConfigurer {

    /**
     * 注册前端安全的 Long 标识符序列化规则。
     *
     * @return FCC 标识符 Jackson 模块
     */
    @Bean
    public Module fccIdentifierJacksonModule() {
        return FccIdentifierJacksonModule.create();
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.getObjectMapper().registerModule(FccIdentifierJacksonModule.create());
            }
        }
    }
}


