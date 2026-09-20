package com.chandler.fcc.admin.bootstrap;

import com.chandler.fcc.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 在部署方明确开启时创建首个系统管理员。
 *
 * <p>该入口只解决空库无法登录的问题，不提供口令重置能力，也不会修改任何已有账号。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final AdminUserService adminUserService;

    /**
     * 执行一次性首个管理员初始化。
     *
     * @param args 应用启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        boolean created = adminUserService.bootstrapFirstAdmin(
            properties.getUsername(),
            properties.getRealName(),
            properties.getPassword()
        );
        if (created) {
            log.info(
                "[管理员初始化] 首个管理员已创建 username={}，请移除 FCC_BOOTSTRAP_ADMIN_* 环境变量并重启服务",
                properties.getUsername().trim()
            );
        } else {
            log.info("[管理员初始化] 目标管理员已存在，未修改账号或口令 username={}", properties.getUsername().trim());
        }
    }
}
