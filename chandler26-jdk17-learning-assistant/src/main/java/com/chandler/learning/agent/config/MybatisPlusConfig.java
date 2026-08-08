package com.chandler.learning.agent.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.chandler.learning.agent.security.LearningAuditor;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 企业级基础配置。
 * <p>
 * 统一处理 DO 审计字段，避免业务代码反复维护创建人、更新人、逻辑删除和版本号。
 */
@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig implements MetaObjectHandler {

    private final LearningAuditor learningAuditor;

    /**
     * 启用 MyBatis-Plus 乐观锁拦截器，让 BaseEntity.version 在并发更新时生效。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 处理 {@code insertFill} 相关业务。
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = learningAuditor.currentUserId();
        strictInsertFill(metaObject, "createBy", Long.class, userId);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateBy", Long.class, userId);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", Boolean.class, false);
        strictInsertFill(metaObject, "version", Integer.class, LearningConstants.Audit.INITIAL_VERSION);
    }

    /**
     * 更新 {@code updateFill} 相关业务。
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateBy", Long.class, learningAuditor.currentUserId());
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
