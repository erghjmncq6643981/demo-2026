package com.chandler.fcc.server.event;

import com.chandler.fcc.server.call.CallRecoveryService;
import com.chandler.fcc.server.event.application.FccEventDispatcher;
import com.chandler.fcc.server.event.infrastructure.EventInboxMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.JetStreamSubscription;
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
    private static final String SUBJECT = "fs.event.>";
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ACK_WAIT = Duration.ofSeconds(90);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

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
     * 直接处理一条已取得的消息，供非 JetStream 入口或测试调用。
     *
     * @param message NATS 原始消息
     */
    public void onMessage(Message message) {
        try {
            dispatcher.dispatch(message.getData());
        } catch (Exception failure) {
            throw new IllegalStateException("事件处理未完成", failure);
        }
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
                var subscription = natsConnection.jetStream().subscribe(SUBJECT, options);
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
                    if (processDurable(message)) message.ack();
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
     * @param message JetStream 消息
     * @return 应当确认消息时返回 {@code true}，非法来源已终止时返回 {@code false}
     * @throws Exception JSON 或数据库不可用
     */
    private boolean processDurable(Message message) throws Exception {
        JsonNode params = objectMapper.readTree(message.getData()).path("params");
        String eventId = params.path("event_id").asText();
        String nodeId = params.path("node_id").asText();
        if (!validIdentity(message, eventId, nodeId)) {
            log.error("[事件消费] 拒绝非法身份消息 subject={}", message.getSubject());
            message.term();
            return false;
        }

        String payload = new String(message.getData(), StandardCharsets.UTF_8);
        if (inbox.receive(eventId, nodeId, payload) == 0) {
            handleDuplicate(eventId);
            return true;
        }

        try {
            dispatcher.dispatch(message.getData());
            inbox.finish(eventId, "PROCESSED");
        } catch (Exception failure) {
            inbox.finish(eventId, "FAILED");
            log.error("[事件消费] 事件失败已留存 eventId={}", eventId, failure);
        }
        return true;
    }

    /**
     * 校验事件标识、节点标识和 NATS 主题归属一致。
     *
     * @param message NATS 消息
     * @param eventId 事件标识
     * @param nodeId 节点标识
     * @return 身份有效时返回 {@code true}
     */
    private boolean validIdentity(Message message, String eventId, String nodeId) {
        return eventId.matches("[a-f0-9]{64}") &&
        !nodeId.isBlank() &&
        message.getSubject().startsWith("fs.event." + nodeId + ".");
    }

    /**
     * 对重复事件保持幂等；遗留 PROCESSING 说明上次处理结果未知，转为人工对账状态。
     *
     * @param eventId 事件标识
     */
    private void handleDuplicate(String eventId) {
        if ("PROCESSING".equals(inbox.status(eventId))) {
            inbox.finish(eventId, "UNKNOWN");
            log.error("[事件消费] 中断事件需要对账 eventId={}", eventId);
        }
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
