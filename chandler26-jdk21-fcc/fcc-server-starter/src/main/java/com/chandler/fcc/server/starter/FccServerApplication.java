package com.chandler.fcc.server.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.chandler.fcc")
public class FccServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FccServerApplication.class, args);
    }
}

