package com.chandler.fcc.admin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由拦截器与安全配置
 *
 * @author Chandler
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final AuthService authService;

    /**
     * 为管理接口注册登录检查和数据库当前状态复核。
     *
     * @param registry MVC 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    authService.revalidateCurrentSession();
                }))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                        "/api/admin/auth/login",
                        // 录音复播与下载由浏览器 <audio>/<a download> 直接发起，无法携带自定义请求头，
                        // 因此按录制事实 ID 开放只读访问；路径合法性由录音服务在共享目录内校验。
                        "/api/admin/recordings/**",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error"
                );
    }
}
