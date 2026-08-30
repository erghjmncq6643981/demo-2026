package com.chandler.learning.agent.security;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.config.security.LearningSecurityProperties;
import com.chandler.learning.agent.exception.LearningAssistantException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void createsTokenWithSevenDaysExpirationByDefault() {
        LearningSecurityProperties properties = new LearningSecurityProperties();
        JwtTokenService service = new JwtTokenService(properties);

        String token = service.createToken(1001L, "chandler");
        assertThat(token).isNotBlank();

        JwtClaims claims = service.parse(token);
        assertThat(claims.userId()).isEqualTo(1001L);
        assertThat(claims.username()).isEqualTo("chandler");

        long daysUntilExpiration = ChronoUnit.DAYS.between(LocalDateTime.now(), claims.expiredTime());
        assertThat(daysUntilExpiration).isGreaterThanOrEqualTo(6).isLessThanOrEqualTo(7);
    }

    @Test
    void rejectsInvalidOrTamperedToken() {
        LearningSecurityProperties properties = new LearningSecurityProperties();
        JwtTokenService service = new JwtTokenService(properties);

        assertThatThrownBy(() -> service.parse(""))
                .isInstanceOf(LearningAssistantException.class)
                .hasFieldOrPropertyWithValue("errorCode", LearningErrorCode.JWT_INVALID.name());

        assertThatThrownBy(() -> service.parse("invalid.token"))
                .isInstanceOf(LearningAssistantException.class)
                .hasFieldOrPropertyWithValue("errorCode", LearningErrorCode.JWT_INVALID.name());
    }
}
