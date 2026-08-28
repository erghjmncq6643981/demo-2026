package com.chandler.learning.agent.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 统一执行接口权限声明，确保权限校验位于 Controller 方法体之外。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PermissionAuthorizationAspect {

    private final CurrentUserContext currentUserContext;

    /** 创建权限切面。 */
    public PermissionAuthorizationAspect(CurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    /** 拦截方法或控制器类上的权限声明。 */
    @Around("execution(public * *(..)) && "
            + "(@annotation(com.chandler.learning.agent.security.RequirePermission) "
            + "|| @within(com.chandler.learning.agent.security.RequirePermission))")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        RequirePermission declaration = findDeclaration(joinPoint);
        if (declaration != null) {
            currentUserContext.requirePermissions(declaration.value());
        }
        return joinPoint.proceed();
    }

    private RequirePermission findDeclaration(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
        RequirePermission methodDeclaration = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
        if (methodDeclaration != null) {
            return methodDeclaration;
        }
        return AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), RequirePermission.class);
    }
}
