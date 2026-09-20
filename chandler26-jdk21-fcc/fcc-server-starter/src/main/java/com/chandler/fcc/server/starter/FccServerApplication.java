package com.chandler.fcc.server.starter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FCC 呼叫控制服务启动入口类
 * <p>
 * 负责拉起 FCC 控制面服务（默认端口 8085），初始化 NATS 消息总线监听、话务状态机驱动引擎、
 * Redis 会话及分机在线态维护以及 MySQL MyBatis-Plus 数据持久层。
 * </p>
 *
 * @author Chandler
 */
@SpringBootApplication(scanBasePackages = "com.chandler.fcc")
@MapperScan(basePackages="com.chandler.fcc.server",annotationClass=org.apache.ibatis.annotations.Mapper.class)
public class FccServerApplication {

    /**
     * Spring Boot 启动主函数
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FccServerApplication.class, args);
    }
}
