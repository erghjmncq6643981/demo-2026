package com.chandler.fcc.admin.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 框架核心配置
 * <p>
 * 注册 MySQL 物理分页插件，自动驱动多条件检索与分页总数计算。
 * </p>
 *
 * @author Chandler
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册分页拦截器
     *
     * @return MybatisPlusInterceptor 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
