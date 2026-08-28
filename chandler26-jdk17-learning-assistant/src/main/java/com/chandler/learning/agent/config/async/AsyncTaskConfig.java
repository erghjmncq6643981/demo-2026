package com.chandler.learning.agent.config.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskDecorator;

/** AI 和批处理任务使用的受控线程池。 */
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class AsyncTaskConfig {

    private final LearningAiTaskProperties properties;
    private final LearningAuditLogProperties auditLogProperties;

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        return createExecutor(
                properties.getCorePoolSize(),
                properties.getMaxPoolSize(),
                properties.getQueueCapacity(),
                properties.getKeepAliveSeconds(),
                properties.getAwaitTerminationSeconds(),
                "learning-ai-",
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 产品内业务日志使用独立线程池，队列满时丢弃日志而不是反向阻断业务请求。 */
    @Bean("auditLogExecutor")
    public Executor auditLogExecutor() {
        RejectedExecutionHandler discardWithWarning = (runnable, executor) -> log.warn(
                "event=system_log_persistence result=dropped reason=executor_saturated active={} queueSize={}",
                executor.getActiveCount(), executor.getQueue().size());
        return createExecutor(
                auditLogProperties.getCorePoolSize(),
                auditLogProperties.getMaxPoolSize(),
                auditLogProperties.getQueueCapacity(),
                auditLogProperties.getKeepAliveSeconds(),
                auditLogProperties.getAwaitTerminationSeconds(),
                "learning-audit-",
                discardWithWarning);
    }

    private ThreadPoolTaskExecutor createExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int keepAliveSeconds,
            int awaitTerminationSeconds,
            String threadNamePrefix,
            RejectedExecutionHandler rejectedExecutionHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }

    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
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
        };
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
