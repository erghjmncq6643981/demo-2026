package com.chandler.fcc.server.flow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 话务流程发布 Redis Pub/Sub 订阅配置
 * <p>
 * 监听 fcc:flow:publish 主题，实现管理台发布流程时呼叫引擎毫秒级全自动热重载。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Configuration
public class RedisFlowSubConfig {

    @Bean
    public RedisMessageListenerContainer flowRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            FlowConfig flowConfig) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                String flowKey = new String(message.getBody()).replace("\"", "").trim();
                log.info("🔔 [Redis 发布订阅] 收到流程发布变更通知: flowKey={}", flowKey);
                flowConfig.reloadFlow(flowKey);
            } catch (Exception e) {
                log.warn("⚠️ [Redis 发布订阅] 处理流程热重载异常: {}", e.getMessage());
            }
        }, new ChannelTopic("fcc:flow:publish"));
        return container;
    }
}
