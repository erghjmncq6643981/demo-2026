package com.chandler.learning.agent.security;

import com.chandler.learning.agent.config.LearningSecurityProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/** 生产类环境启动时阻止使用开发默认密钥。 */
@Component
@RequiredArgsConstructor
public class ProductionSecretValidator {

    private static final Set<String> GUARDED_PROFILES = Set.of("prod", "pre");
    private static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;
    private final LearningSecurityProperties properties;

    @PostConstruct
    public void validate() {
        boolean guarded = Arrays.stream(environment.getActiveProfiles()).anyMatch(GUARDED_PROFILES::contains);
        if (!guarded) {
            return;
        }
        requireProductionSecret("LEARNING_JWT_SECRET", properties.getJwtSecret());
        requireProductionSecret("LEARNING_API_KEY_SECRET", properties.getApiKeySecret());
    }

    private void requireProductionSecret(String environmentName, String value) {
        if (!StringUtils.hasText(value)
                || value.length() < MIN_SECRET_LENGTH
                || value.startsWith("dev-learning-assistant-")) {
            throw new IllegalStateException(environmentName
                    + " must be explicitly configured with at least " + MIN_SECRET_LENGTH
                    + " characters in prod/pre profiles");
        }
    }
}
