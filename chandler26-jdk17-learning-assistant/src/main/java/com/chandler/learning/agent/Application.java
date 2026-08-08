
package com.chandler.learning.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 英语学习助手后端启动入口。
 */
@MapperScan({"com.chandler.learning.agent.mapper"})
@SpringBootApplication(scanBasePackages = "com.chandler")
public class Application {
	/**
	 * 处理 {@code main} 相关业务。
	 */
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
