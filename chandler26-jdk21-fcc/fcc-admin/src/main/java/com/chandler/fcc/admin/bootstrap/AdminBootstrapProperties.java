package com.chandler.fcc.admin.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 首个管理账号的一次性启动配置。
 *
 * <p>所有值只从部署环境读取。口令仅用于首次创建，既不会写入日志，也不会作为默认值进入源码。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fcc.bootstrap.admin")
public class AdminBootstrapProperties {

    /**
     * 是否执行首个管理员初始化。
     */
    private boolean enabled;

    /**
     * 首个管理员登录账号。
     */
    private String username;

    /**
     * 首个管理员显示姓名。
     */
    private String realName = "系统管理员";

    /**
     * 首个管理员明文口令，仅在应用启动期间使用。
     */
    private String password;
}
