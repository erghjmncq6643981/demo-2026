package com.chandler.fcc.admin.config;

import com.chandler.fcc.common.recording.RecordingStorageProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 录音共享存储配置装配
 * <p>
 * 管理端只读访问录音文件，需要与 fcc-server 使用同一份 {@code fcc.recording.*} 配置，
 * 以便对数据库中的录音地址做共享目录范围内的合法性校验。
 * </p>
 *
 * @author Chandler
 */
@Configuration
public class RecordingStorageConfig {

    /**
     * 注册并绑定录音存储配置
     *
     * @return 录音存储配置对象
     */
    @Bean
    @ConfigurationProperties(prefix = "fcc.recording")
    public RecordingStorageProperties recordingStorageProperties() {
        return new RecordingStorageProperties();
    }
}
