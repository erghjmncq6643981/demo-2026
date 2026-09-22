package com.chandler.fcc.server.command;

import com.chandler.fcc.common.dto.command.FNodeBridgeDTO;
import com.chandler.fcc.common.dto.command.FNodeCommandResultDTO;
import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.dto.command.FNodeNativeApiDTO;
import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.FNodeRecordDTO;
import com.chandler.fcc.common.dto.command.FNodeTransferDTO;
import com.chandler.fcc.common.dto.rpc.JsonRpcRequest;
import com.chandler.fcc.common.dto.rpc.JsonRpcResponse;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.protocol.FNodeMethod;
import com.chandler.fcc.common.protocol.NatsSubjectFactory;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.infrastructure.nats.FccProperties;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FCC 逻辑话务命令客户端。
 *
 * <p>本客户端不接收或计算 FreeSWITCH 节点标识。所有命令发送到逻辑分发主题，
 * Sidecar Coordinator 负责根据新建呼叫的调度策略或已有话道的归属选择节点。
 * 应答中的 {@code node_id} 是基础设施事实，只用于审计、诊断和运行时归属。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FccClient {

    private final Connection natsConnection;
    private final FccProperties fccProperties;
    private final ObjectMapper objectMapper = new ObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    );
    private final CallPersistenceService callPersistenceService;

    /**
     * 发起逻辑外呼命令。
     *
     * @param dto 外呼指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult dial(FNodeDialDTO dto) {
        String uuid = dto.getUuid();
        if (
            (uuid == null || uuid.isBlank()) &&
            dto.getDestination() != null &&
            dto.getDestination().getCallParams() != null &&
            !dto.getDestination().getCallParams().isEmpty()
        ) {
            uuid = dto.getDestination().getCallParams().getFirst().getUuid();
        }
        if (uuid == null || uuid.isBlank()) {
            uuid = IdUtil.getUuid();
            dto.setUuid(uuid);
        }
        return sendRequest(FNodeMethod.DIAL, dto, "dial-" + uuid);
    }

    /**
     * 请求逻辑桥接两个话道。
     *
     * @param ctrlUuid 控制流程标识
     * @param uuid 主话道 UUID
     * @param peerUuid 对端话道 UUID
     * @return Sidecar 受理结果
     */
    public FNodeResult channelBridge(String ctrlUuid, String uuid, String peerUuid) {
        return sendRequest(
            FNodeMethod.CHANNEL_BRIDGE,
            FNodeBridgeDTO.builder().ctrlUuid(ctrlUuid).uuid(uuid).peerUuid(peerUuid).build(),
            IdUtil.getCommandId()
        );
    }

    /**
     * 收取逻辑话道上的 DTMF。
     *
     * @param dto 收号指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult readDTMF(FNodeReadDTMFDTO dto) {
        return sendRequest(FNodeMethod.READ_DTMF, dto, IdUtil.getCommandId());
    }

    /**
     * 使用稳定命令标识收取 DTMF，供流程重试复用原命令。
     *
     * @param dto 收号指令参数
     * @param commandId 稳定命令标识
     * @return Sidecar 受理结果
     */
    public FNodeResult readDTMF(FNodeReadDTMFDTO dto, String commandId) {
        return sendRequest(FNodeMethod.READ_DTMF, dto, commandId);
    }

    /**
     * 播放媒体或文本提示。
     *
     * @param dto 放音指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult play(FNodePlayDTO dto) {
        return sendRequest(FNodeMethod.PLAY, dto, IdUtil.getCommandId());
    }

    /**
     * 控制逻辑话道录音。
     *
     * @param dto 录音指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult record(FNodeRecordDTO dto) {
        return sendRequest(FNodeMethod.RECORD, dto, IdUtil.getCommandId());
    }

    /**
     * 请求逻辑话道挂机。
     *
     * @param ctrlUuid 控制流程标识
     * @param uuid 目标话道 UUID
     * @param cause 挂机原因
     * @return Sidecar 受理结果
     */
    public FNodeResult hangup(String ctrlUuid, String uuid, String cause) {
        FNodeHangupDTO dto = FNodeHangupDTO.builder()
            .ctrlUuid(ctrlUuid)
            .uuid(uuid)
            .cause(cause == null ? "NORMAL_CLEARING" : cause)
            .build();
        return sendRequest(FNodeMethod.HANGUP, dto, IdUtil.getCommandId());
    }

    /**
     * 执行受控的 FreeSWITCH 原生 API 逃生通道。
     *
     * @param cmd 原生指令名称
     * @param args 原生指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult nativeAPI(String cmd, String args) {
        return sendRequest(
            FNodeMethod.NATIVE_API,
            FNodeNativeApiDTO.builder().cmd(cmd).args(args).build(),
            IdUtil.getCommandId()
        );
    }

    /**
     * 查询底层节点状态，供运维查询使用。
     *
     * @return Sidecar 状态结果
     */
    public FNodeResult status() {
        return sendRequest(FNodeMethod.STATUS, null, IdUtil.getCommandId());
    }

    /**
     * 查询已持久化的原命令结果，不重放副作用。
     *
     * @param commandId 原命令标识
     * @return 原命令结果
     */
    public FNodeResult commandResult(String commandId) {
        return sendRequest(
            FNodeMethod.COMMAND_RESULT,
            FNodeCommandResultDTO.builder().commandId(commandId).build(),
            IdUtil.getCommandId()
        );
    }

    /**
     * 查询所有 Sidecar 聚合后的完整话道快照。
     *
     * @return 完整话道快照结果
     */
    public FNodeResult channelSnapshot() {
        return sendRequest(FNodeMethod.CHANNEL_SNAPSHOT, null, IdUtil.getCommandId());
    }

    /**
     * 使用公共 FNode 方法和稳定命令标识执行动作。
     *
     * @param method 规范 FNode 方法
     * @param params 与方法匹配的公共 DTO
     * @param commandId 稳定命令标识
     * @return Sidecar 受理结果
     */
    public FNodeResult execute(FNodeMethod method, Object params, String commandId) {
        return sendRequest(method, params, commandId);
    }

    /**
     * 使用 Sidecar 的规范转接方法转接话道。
     *
     * @param params 已校验的转接参数
     * @return Sidecar 受理结果
     */
    public FNodeResult transfer(FNodeTransferDTO params) {
        return sendRequest(FNodeMethod.TRANSFER, params, IdUtil.getCommandId());
    }

    /**
     * 发送逻辑命令并记录同步应答审计。
     *
     * @param method 规范 FNode 方法
     * @param params 方法参数
     * @param commandId 幂等命令标识
     * @return Sidecar 结果；通信未知时返回 -32000
     */
    private FNodeResult sendRequest(FNodeMethod method, Object params, String commandId) {
        if (method == null || commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("FNode 方法和稳定命令标识不能为空");
        }
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(commandId)
            .method(method.getWireName())
            .params(params)
            .build();
        String subject = NatsSubjectFactory.commandDispatch();
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(request);
            log.debug("[FCC -> NATS] subject={}, method={}, commandId={}", subject, method.getWireName(), commandId);
            Message reply = natsConnection.request(
                subject,
                payload,
                Duration.ofMillis(fccProperties.getRpcTimeoutMillis())
            );
            if (reply == null) {
                log.warn("[FCC] RPC 超时 method={}, commandId={}", method.getWireName(), commandId);
                return unknownResult("RPC timeout");
            }
            String responsePayload = new String(reply.getData(), StandardCharsets.UTF_8);
            JsonRpcResponse response = objectMapper.readValue(responsePayload, JsonRpcResponse.class);
            FNodeResult result = toResult(response);
            recordAudit(commandId, method, payload, responsePayload, result);
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unknownResult("Interrupted");
        } catch (Exception failure) {
            log.error("[FCC] RPC 通信异常 method={}, commandId={}: {}", method.getWireName(), commandId, failure.getMessage());
            return unknownResult(failure.getMessage());
        }
    }

    /**
     * 将 JSON-RPC 应答转换为统一 FNode 结果。
     *
     * @param response JSON-RPC 应答
     * @return FNode 结果
     */
    private FNodeResult toResult(JsonRpcResponse response) {
        if (response == null) {
            return unknownResult("Empty RPC response");
        }
        if (response.getError() != null) {
            return FNodeResult.builder()
                .code(response.getError().getCode())
                .message(response.getError().getMessage())
                .data(response.getError().getData())
                .build();
        }
        return response.getResult();
    }

    /**
     * 记录命令审计，节点标识只能使用 Sidecar 应答事实。
     *
     * @param commandId 命令标识
     * @param method FNode 方法
     * @param requestPayload 请求报文
     * @param responsePayload 应答报文
     * @param result 解析结果
     */
    private void recordAudit(
        String commandId,
        FNodeMethod method,
        byte[] requestPayload,
        String responsePayload,
        FNodeResult result
    ) {
        if (callPersistenceService == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            callPersistenceService.recordCommand(
                CallCommandEntity.builder()
                    .commandId(commandId)
                    .idempotencyKey(commandId)
                    .assignedNodeId(result == null ? null : result.getNodeId())
                    .methodName(method.getWireName())
                    .requestPayload(new String(requestPayload, StandardCharsets.UTF_8))
                    .responsePayload(responsePayload)
                    .status(result != null && result.isSuccess() ? "ACCEPTED" : "FAILED")
                    .sentAt(now)
                    .completedAt(now)
                    .build()
            );
        } catch (Exception auditFailure) {
            log.debug("[FCC] 命令审计写入失败: {}", auditFailure.getMessage());
        }
    }

    /**
     * 创建通信未知结果。
     *
     * @param message 未知原因
     * @return -32000 结果
     */
    private FNodeResult unknownResult(String message) {
        return FNodeResult.builder().code(-32000).message(message).build();
    }
}
