package com.chandler.learning.agent.config.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 覆盖自动推导的接口名称；切面会为所有 Controller 输出不含请求体的访问摘要。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiAccessLog {

    /** 可读的业务接口名；为空时使用 Swagger Operation 摘要或方法名。 */
    String value() default "";
}
