package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.learning.domain.constant.LearningActivityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 定期异步投影活动事件，并恢复异常中断的事件。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningActivityProjector {

    private final LearningActivityAggregationService aggregationService;

    @Qualifier("learningEventExecutor")
    private final Executor learningEventExecutor;

    private final AtomicBoolean running = new AtomicBoolean();

    /** 将待处理活动事件提交到学习事件线程池。 */
    @Scheduled(fixedDelayString = "${learning.activity.projector-delay-ms:5000}")
    public void scheduleProjection() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    int processed = aggregationService.processPendingBatch(LearningActivityConstants.PROJECT_BATCH_SIZE);
                    if (processed > 0) {
                        log.info("event=learning_activity_projection result=success count={}", processed);
                    }
                } catch (RuntimeException ex) {
                    log.warn("event=learning_activity_projection result=failed error={}",
                            ex.getClass().getSimpleName());
                    log.debug("学习活动异步汇总失败", ex);
                } finally {
                    running.set(false);
                }
            }, learningEventExecutor);
        } catch (RuntimeException ex) {
            running.set(false);
            log.warn("event=learning_activity_projection result=rejected error={}",
                    ex.getClass().getSimpleName());
        }
    }

    /** 恢复进程重启或执行器拒绝后遗留的 processing 事件。 */
    @Scheduled(fixedDelayString = "${learning.activity.recovery-delay-ms:30000}")
    public void recoverStaleEvents() {
        try {
            int reset = aggregationService.resetStaleProcessing(LocalDateTime.now().minusMinutes(5));
            if (reset > 0) {
                log.info("event=learning_activity_recovery result=success count={}", reset);
            }
        } catch (RuntimeException ex) {
            log.warn("event=learning_activity_recovery result=failed error={}",
                    ex.getClass().getSimpleName());
            log.debug("学习活动事件恢复失败", ex);
        }
    }
}
