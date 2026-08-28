package com.chandler.learning.agent.exception;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class LearningAssistantExceptionTest {

    @Test
    void shouldUseStatusAndMessageDefinedByErrorCode() {
        LearningAssistantException exception = LearningAssistantException.badRequest(
                LearningErrorCode.AUTH_INVALID_CREDENTIALS);

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getErrorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(exception.getMessage()).isEqualTo("用户名或密码错误");
    }
}
