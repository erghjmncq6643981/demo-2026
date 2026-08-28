package com.chandler.learning.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lombok.RequiredArgsConstructor;

/** AI 和批处理任务使用的受控线程池。 */
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class AsyncTaskConfig {

    private final LearningAiTaskProperties properties;

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix("learning-ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (context == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(context);
                    }
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    /** 独立于 AI 工作线程池的租约心跳线程，避免心跳被排队任务阻塞。 */
    @Bean(name = "aiTaskLeaseScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService aiTaskLeaseScheduler() {
        return Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "learning-ai-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
