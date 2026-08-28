package com.chandler.learning.agent.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口或控制器所需要的权限，由 {@link PermissionAuthorizationAspect} 统一校验。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 当前接口需要同时具备的权限。 */
    LearningPermission[] value();
}
