package com.chandler.learning.agent.config.async;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 产品内业务日志异步持久化线程池配置。
 * <p>
 * 审计日志与 AI 任务隔离，避免批量模型任务占满工作线程时拖慢用户操作。
 */
@Data
@Component
@ConfigurationProperties(prefix = "learning.audit-log")
public class LearningAuditLogProperties {

    /** 常驻工作线程数。 */
    private int corePoolSize = 1;

    /** 最大工作线程数。 */
    private int maxPoolSize = 2;

    /** 有界等待队列容量。 */
    private int queueCapacity = 200;

    /** 空闲线程存活秒数。 */
    private int keepAliveSeconds = 30;

    /** 关闭服务时等待在途任务的秒数。 */
    private int awaitTerminationSeconds = 10;

    /** Outbox 恢复轮询间隔毫秒。 */
    private long recoveryDelayMs = 30_000L;
}
