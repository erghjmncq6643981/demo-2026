package com.chandler.fcc.server.infrastructure.nats;

import io.nats.client.Connection;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * NATS 消息总线客户端配置类
 * <p>
 * 构建与初始化与 NATS 消息集群的长连接 Connection Bean，配置自动重连、心跳保活及错误监听。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Configuration
public class FccNatsConfiguration {

    /**
     * 注册 NATS Connection 单例 Bean
     *
     * @param properties FCC 配置属性
     * @return NATS 客户端连接实例
     * @throws Exception 当网络无法连接或配置非法时抛出异常
     */
    @Bean(destroyMethod = "close")
    public Connection natsConnection(FccProperties properties) throws Exception {
        log.info("🔌 [FCC] 正在建立 NATS 消息总线连接: {}", properties.getNatsUrl());
        Options options = new Options.Builder()
                .server(properties.getNatsUrl())
                .connectionName("chandler26-jdk21-fcc-server")
                .connectionTimeout(Duration.ofSeconds(5))
                .pingInterval(Duration.ofSeconds(10))
                .reconnectWait(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .connectionListener((conn, type) -> log.info("🔄 [FCC NATS] 连接状态变更: {}", type))
                .errorListener(new ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("❌ [FCC NATS] 发生总线错误: {}", error);
                    }
                })
                .build();

        Connection connection = Nats.connect(options);
        log.info("✅ [FCC] 成功连接至 NATS 总线: {}", connection.getConnectedUrl());
        return connection;
    }
}
