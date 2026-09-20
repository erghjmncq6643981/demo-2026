package com.chandler.fcc.server.event;

import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.NatsSubjectFactory;
import com.chandler.fcc.server.call.CallRecoveryService;
import com.chandler.fcc.server.event.application.FccEventDispatcher;
import com.chandler.fcc.server.event.domain.EventInboxStatus;
import com.chandler.fcc.server.event.infrastructure.EventInboxMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 负责 FCC_EVENTS JetStream 的有界拉取、来源校验和收件箱状态维护。
 *
 * <p>具体 Channel、DTMF、注册和录音业务由类型处理器完成，本类不包含 SQL 或话务流程判断。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FccEventListener {

    private static final String STREAM_NAME = "FCC_EVENTS";
    private static final String CONSUMER_NAME = "fcc-control";
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ACK_WAIT = Duration.ofSeconds(90);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private static final int MAX_PROCESSING_ATTEMPTS = 5;

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final EventInboxMapper inbox;
    private final CallRecoveryService recovery;
    private final FccEventDispatcher dispatcher;
    private final ExecutorService consumer = Executors.newSingleThreadExecutor(runnable ->
        Thread.ofPlatform().name("fcc-event-consumer").daemon(true).unstarted(runnable)
    );

    private volatile boolean running = true;

    /**
     * 服务启动后在独立单线程中恢复通话并拉取持久事件。
     */
    @PostConstruct
    public void startListening() {
        consumer.submit(this::consumeLoop);
    }

    /**
     * 停止拉取并中断消费者线程。
     */
    @PreDestroy
    public void stopListening() {
        running = false;
        consumer.shutdownNow();
    }

    /**
     * 持续建立 JetStream 拉取订阅；连接故障后按固定间隔重建。
     */
    private void consumeLoop() {
        while (running) {
            try {
                recovery.restore();
                var options = PullSubscribeOptions.builder()
                    .stream(STREAM_NAME)
                    .durable(CONSUMER_NAME)
                    .configuration(
                        ConsumerConfiguration.builder()
                            .ackPolicy(AckPolicy.Explicit)
                            .ackWait(ACK_WAIT)
                            .maxAckPending(1)
                            .maxDeliver(-1)
                            .build()
                    )
                    .build();
                var subscription = natsConnection.jetStream().subscribe(NatsSubjectFactory.allEvents(), options);
                consume(subscription);
            } catch (Exception failure) {
                if (!running) return;
                log.warn("[事件消费] 等待 FCC_EVENTS 流可用 type={}", failure.getClass().getSimpleName());
                waitBeforeReconnect();
            }
        }
    }

    /**
     * 从当前订阅逐条拉取事件，保证同一实例内顺序处理。
     *
     * @param subscription JetStream 拉取订阅
     * @throws Exception 拉取连接失效
     */
    private void consume(JetStreamSubscription subscription) throws Exception {
        while (running) {
            for (Message message : subscription.fetch(1, FETCH_TIMEOUT)) {
                try {
                    if (processDurable(message.getSubject(), message.getData())) {
                        message.ack();
                    } else {
                        message.term();
                    }
                } catch (Exception failure) {
                    message.nakWithDelay(RETRY_DELAY);
                    log.warn("[事件消费] 等待重试 type={}", failure.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * 校验事件来源，将原始信封写入收件箱，再调用类型分发器。
     *
     * @param subject NATS 事件主题
     * @param payload 原始事件信封
     * @return 应当确认消息时返回 {@code true}，非法来源应终止时返回 {@code false}
     * @throws Exception JSON 或数据库不可用
     */
    boolean processDurable(String subject, byte[] payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode params = root.path("params");
        FccEventMethod method = FccEventMethod.fromWireName(root.path("method").asText());
        String eventId = params.path(FccEventField.EVENT_ID.getWireName()).asText();
        String nodeId = params.path(FccEventField.NODE_ID.getWireName()).asText();
        if (!validIdentity(subject, eventId, nodeId, method)) {
            log.error("[事件消费] 拒绝非法身份消息 subject={}", subject);
            return false;
        }

        String envelope = new String(payload, StandardCharsets.UTF_8);
        if (!claim(eventId, nodeId, envelope)) {
            return true;
        }

        try {
            dispatcher.dispatch(payload);
        } catch (Exception failure) {
            inbox.finish(
                eventId,
                EventInboxStatus.FAILED.getDatabaseValue(),
                failure.getClass().getSimpleName()
            );
            throw failure;
        }
        if (
            inbox.finish(
                eventId,
                EventInboxStatus.PROCESSED.getDatabaseValue(),
                null
            ) !=
            1
        ) {
            throw new IllegalStateException("事件处理结果无法持久化");
        }
        return true;
    }

    /**
     * 校验事件标识、节点标识和 NATS 主题归属一致。
     *
     * @param subject NATS 事件主题
     * @param eventId 事件标识
     * @param nodeId 节点标识
     * @param method 规范事件方法
     * @return 身份有效时返回 {@code true}
     */
    private boolean validIdentity(
        String subject,
        String eventId,
        String nodeId,
        FccEventMethod method
    ) {
        return eventId.matches("[a-f0-9]{64}") &&
            !nodeId.isBlank() &&
            subject.equals(NatsSubjectFactory.event(nodeId, method));
    }

    /**
     * 首次领取事件或原子领取可重试失败；其余状态保持幂等。
     *
     * @param eventId 事件标识
     * @param nodeId 节点标识
     * @param payload 原始事件信封
     * @return 当前消息需要进入业务分发时返回 {@code true}
     */
    private boolean claim(String eventId, String nodeId, String payload) {
        if (inbox.receive(eventId, nodeId, payload) == 1) {
            return true;
        }
        EventInboxStatus status = EventInboxStatus.fromDatabaseValue(inbox.status(eventId));
        if (
            status == EventInboxStatus.FAILED &&
            inbox.claimRetry(eventId, MAX_PROCESSING_ATTEMPTS) == 1
        ) {
            return true;
        }
        if (status == EventInboxStatus.PROCESSING) {
            inbox.finish(
                eventId,
                EventInboxStatus.UNKNOWN.getDatabaseValue(),
                "PREVIOUS_PROCESS_INTERRUPTED"
            );
            log.error("[事件消费] 中断事件需要对账 eventId={}", eventId);
        } else if (status == EventInboxStatus.FAILED) {
            inbox.finish(
                eventId,
                EventInboxStatus.UNKNOWN.getDatabaseValue(),
                "MAX_PROCESSING_ATTEMPTS_REACHED"
            );
            log.error("[事件消费] 事件重试耗尽需要对账 eventId={}", eventId);
        }
        return false;
    }

    /**
     * 等待连接重建，并在关闭服务时及时退出。
     */
    private void waitBeforeReconnect() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
