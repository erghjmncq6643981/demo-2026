package com.chandler.learning.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "learning.security")
public class LearningSecurityProperties {

    private String jwtSecret = "dev-learning-assistant-jwt-secret-change-me";

    private String jwtIssuer = "learning-assistant";

    private Integer jwtExpireDays = 30;

    private String apiKeySecret = "dev-learning-assistant-api-key-secret-change-me";
}
