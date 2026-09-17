package com.chandler.fcc.admin.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.chandler.fcc")
public class FccAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(FccAdminApplication.class, args);
    }
}

