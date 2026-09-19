package com.chandler.fcc.server.config;

import com.chandler.fcc.common.recording.RecordingStorageProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 录音共享存储配置装配
 * <p>
 * 把无框架依赖的 {@link RecordingStorageProperties} 契约对象绑定到 {@code fcc.recording.*} 配置项。
 * 路径语义定义在 fcc-common，保证 fcc-server 写入的路径与 fcc-admin 读取的路径完全一致。
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
