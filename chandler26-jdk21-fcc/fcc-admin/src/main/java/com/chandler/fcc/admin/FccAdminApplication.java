package com.chandler.fcc.admin;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FCC 运营管理平台服务启动入口类
 * <p>
 * 负责拉起 FCC 运营管理后台（默认端口 8089），初始化通信资源配置、
 * 坐席组织与技能组管理、Go Sidecar 管理面 HTTP 交互、通话 CDR 检索及系统动态配置中心。
 * </p>
 *
 * @author Chandler
 */
@SpringBootApplication(scanBasePackages = "com.chandler.fcc")
@MapperScan(
    basePackages = "com.chandler.fcc.admin",
    annotationClass = Mapper.class
)
public class FccAdminApplication {

    /**
     * Spring Boot 启动主函数
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FccAdminApplication.class, args);
    }
}
