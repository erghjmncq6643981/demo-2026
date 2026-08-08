package com.chandler.learning.agent.config;

import com.chandler.learning.agent.support.LearningConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LearningSecurityProperties 类。
 */
@Data
@Component
@ConfigurationProperties(prefix = "learning.security")
public class LearningSecurityProperties {

    private String jwtSecret = "dev-learning-assistant-jwt-secret-change-me";

    private String jwtIssuer = "learning-assistant";

    private Integer jwtExpireDays = LearningConstants.DEFAULT_JWT_EXPIRE_DAYS;

    private String apiKeySecret = "dev-learning-assistant-api-key-secret-change-me";
}
