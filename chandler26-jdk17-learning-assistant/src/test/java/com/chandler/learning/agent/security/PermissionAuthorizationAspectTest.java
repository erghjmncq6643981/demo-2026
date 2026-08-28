package com.chandler.learning.agent.security;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionAuthorizationAspectTest {

    private final CurrentUserContext currentUserContext = new CurrentUserContext();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsOrdinaryUserBeforeSecuredEndpointRuns() {
        authenticate(user("USER"));
        SecuredEndpoint target = new SecuredEndpoint();
        Endpoint endpoint = proxy(target);

        assertThatThrownBy(endpoint::manage)
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo("ADMIN_REQUIRED");
        assertThat(target.invoked).isFalse();
    }

    @Test
    void allowsAdministratorToAccessDeclaredPermission() {
        authenticate(user("ADMIN"));
        SecuredEndpoint target = new SecuredEndpoint();
        Endpoint endpoint = proxy(target);

        assertThat(endpoint.manage()).isEqualTo("ok");
        assertThat(target.invoked).isTrue();
    }

    @Test
    void appliesClassLevelPermissionDeclarationToEveryEndpoint() {
        authenticate(user("USER"));
        ClassSecuredEndpoint target = new ClassSecuredEndpoint();

        assertThatThrownBy(() -> proxy(target).manage())
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo("ADMIN_REQUIRED");
        assertThat(target.invoked).isFalse();
    }

    private Endpoint proxy(Endpoint target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(false);
        factory.addAspect(new PermissionAuthorizationAspect(currentUserContext));
        return factory.getProxy();
    }

    private void authenticate(LearningUser user) {
        LearningUserPrincipal principal = new LearningUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "token", principal.getAuthorities()));
    }

    private LearningUser user(String roleCode) {
        LearningUser user = new LearningUser();
        user.setId(1001L);
        user.setUsername("chandler");
        user.setRoleCode(roleCode);
        user.setEnabled(true);
        return user;
    }

    private interface Endpoint {

        String manage();
    }

    public static class SecuredEndpoint implements Endpoint {

        private boolean invoked;

        @RequirePermission(LearningPermission.SYSTEM_ADMIN)
        public String manage() {
            invoked = true;
            return "ok";
        }
    }

    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    public static class ClassSecuredEndpoint implements Endpoint {

        private boolean invoked;

        @Override
        public String manage() {
            invoked = true;
            return "ok";
        }
    }
}
