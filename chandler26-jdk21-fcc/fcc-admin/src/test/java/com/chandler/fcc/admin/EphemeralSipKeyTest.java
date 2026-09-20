package com.chandler.fcc.admin;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 仅为测试进程生成临时加密密钥；不读取或写出部署密钥。 */
abstract class EphemeralSipKeyTest {
    private static final String KEY = Base64.getEncoder().encodeToString(
            new SecureRandom().generateSeed(32));

    /** 将临时密钥注入 Spring 测试环境。
     * @param registry 测试属性注册器
     */
    @DynamicPropertySource
    static void configureKey(DynamicPropertyRegistry registry) {
        registry.add("fcc.sip.encryption-key", () -> KEY);
    }
}
