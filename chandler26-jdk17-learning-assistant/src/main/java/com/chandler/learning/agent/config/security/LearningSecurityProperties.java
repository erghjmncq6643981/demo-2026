package com.chandler.learning.agent.config.security;

import com.chandler.learning.agent.security.constant.JwtConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全认证配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "learning.security")
public class LearningSecurityProperties {

    private String jwtSecret = "dev-learning-assistant-jwt-secret-change-me";

    private String jwtIssuer = "learning-assistant";

    private Integer jwtExpireDays = JwtConstants.DEFAULT_EXPIRE_DAYS;

    private String apiKeySecret = "dev-learning-assistant-api-key-secret-change-me";
}
