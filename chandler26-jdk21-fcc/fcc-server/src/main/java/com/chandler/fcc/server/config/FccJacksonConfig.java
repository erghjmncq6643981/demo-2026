package com.chandler.fcc.server.config;

import com.chandler.fcc.common.json.FccIdentifierJacksonModule;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    /**
     * 创建供话务协议、事件和 WebSocket 适配器使用的 Jackson 2 映射器。
     *
     * <p>Spring Boot 4 默认装配 Jackson 3，现有 FCC 协议对象仍使用 Jackson 2，
     * 因此在迁移完成前需要显式提供对应 Bean。</p>
     *
     * @param identifierModule FCC 长整型标识序列化模块
     * @return 已注册 Java 时间与 FCC 标识规则的映射器
    */
    @Bean
    public ObjectMapper fccObjectMapper(
        @Qualifier("fccIdentifierJacksonModule") Module identifierModule
    ) {
        return new ObjectMapper()
            .findAndRegisterModules()
            .registerModule(identifierModule);
    }

    /**
     * 将 FCC 标识序列化规则注册到 Spring MVC 的 Jackson 2 转换器。
     *
     * @param converters 当前服务已装配的 HTTP 消息转换器
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.getObjectMapper().registerModule(FccIdentifierJacksonModule.create());
            }
        }
    }
}


