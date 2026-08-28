package com.chandler.learning.agent.config.async;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AI 和批处理线程池配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "learning.ai.task")
public class LearningAiTaskProperties {

    /** 常驻工作线程数。 */
    private int corePoolSize = 2;

    /** 最大工作线程数。 */
    private int maxPoolSize = 4;

    /** 有界等待队列容量。 */
    private int queueCapacity = 50;

    /** 空闲线程存活秒数。 */
    private int keepAliveSeconds = 60;

    /** 关闭服务时等待在途任务的秒数。 */
    private int awaitTerminationSeconds = 30;
}
