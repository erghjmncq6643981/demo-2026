package com.chandler.learning.agent.security;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserContextTest {

    private final CurrentUserContext currentUserContext = new CurrentUserContext();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsUserAlreadyVerifiedByJwtFilter() {
        LearningUser user = user(1001L, "USER");
        authenticate(user);

        assertThat(currentUserContext.requireUser()).isSameAs(user);
        assertThat(currentUserContext.findUser()).containsSame(user);
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        assertThatThrownBy(currentUserContext::requireUser)
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo("AUTH_REQUIRED");
    }

    @Test
    void rejectsOrdinaryUserFromAdminResource() {
        authenticate(user(1001L, "USER"));

        assertThatThrownBy(currentUserContext::requireAdmin)
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo("ADMIN_REQUIRED");
    }

    private void authenticate(LearningUser user) {
        LearningUserPrincipal principal = new LearningUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "token", principal.getAuthorities()));
    }

    private LearningUser user(Long id, String roleCode) {
        LearningUser user = new LearningUser();
        user.setId(id);
        user.setUsername("chandler");
        user.setRoleCode(roleCode);
        user.setEnabled(true);
        return user;
    }
}
