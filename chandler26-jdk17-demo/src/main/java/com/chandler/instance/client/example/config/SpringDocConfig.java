package com.chandler.instance.client.example.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author 钱丁君-chandler 2025/12/16
 */
@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info().title("示例工程 Demo")
                        .description("示例工程") // API描述信息
                        .version("v1") // API版本号
                        .contact(new Contact().name("chandler"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                        .summary("示例接口"))
                .components(new Components()
                        .addSecuritySchemes("Authorization",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .name("Authorization")
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Bearer Token 认证")))
                // 添加全局安全需求
                .addSecurityItem(new SecurityRequirement().addList("Authorization")); // API许可信息
    }
}
