package com.chandler.fengteng.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 风腾通道机 PDA 协议 Mock 服务入口。 */
@SpringBootApplication
public class FengtengMockApplication {

    /** 启动 Mock 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(FengtengMockApplication.class, args);
    }
}
