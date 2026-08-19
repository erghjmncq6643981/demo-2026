package com.chandler.learning.agent.security;

import com.chandler.learning.agent.config.LearningSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretValidatorTest {

    @Test
    void allowsDevelopmentDefaultsOutsideProductionProfiles() {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "local");
        var properties = new LearningSecurityProperties();

        assertThatCode(() -> new ProductionSecretValidator(environment, properties).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentDefaultsInProduction() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var properties = new LearningSecurityProperties();

        assertThatThrownBy(() -> new ProductionSecretValidator(environment, properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEARNING_JWT_SECRET");
    }

    @Test
    void acceptsExplicitStrongSecretsInProduction() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var properties = new LearningSecurityProperties();
        properties.setJwtSecret("jwt-0123456789-abcdefghijklmnopqrstuvwxyz");
        properties.setApiKeySecret("api-0123456789-abcdefghijklmnopqrstuvwxyz");

        assertThatCode(() -> new ProductionSecretValidator(environment, properties).validate())
                .doesNotThrowAnyException();
    }
}
