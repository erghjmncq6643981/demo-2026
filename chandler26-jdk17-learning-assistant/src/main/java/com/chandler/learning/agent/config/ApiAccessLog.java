package com.chandler.learning.agent.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要输出业务接口访问摘要的 Controller。请求体和模型内容不会写入日志。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiAccessLog {

    /** 可读的业务接口名；为空时使用 Swagger Operation 摘要或方法名。 */
    String value() default "";
}
