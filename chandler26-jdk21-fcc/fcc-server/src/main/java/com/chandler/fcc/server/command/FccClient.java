package com.chandler.fcc.server.command;

import com.chandler.fcc.common.dto.command.FNodeAnswerDTO;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
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
     * 应答指定逻辑话道。
     *
     * @param dto 应答指令参数
     * @return Sidecar 受理结果；真实应答状态以 {@code Event.Channel/ANSWERED} 为准
     */
    public FNodeResult answer(FNodeAnswerDTO dto) {
        return sendRequest(
            FNodeMethod.ANSWER,
            dto,
            stableCommandId("answer", dto.getCtrlUuid(), dto.getUuid())
        );
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
            stableCommandId("bridge", ctrlUuid, uuid, peerUuid)
        );
    }

    /**
     * 收取逻辑话道上的 DTMF。
     *
     * @param dto 收号指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult readDTMF(FNodeReadDTMFDTO dto) {
        return sendRequest(
            FNodeMethod.READ_DTMF,
            dto,
            stableCommandId(
                "dtmf",
                dto.getCtrlUuid(),
                dto.getUuid(),
                dto.getMedia() == null ? null : dto.getMedia().getType(),
                dto.getMedia() == null ? null : dto.getMedia().getData()
            )
        );
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
        return sendRequest(
            FNodeMethod.PLAY,
            dto,
            stableCommandId(
                "play",
                dto.getCtrlUuid(),
                dto.getUuid(),
                dto.getMedia() == null ? null : dto.getMedia().getType(),
                dto.getMedia() == null ? null : dto.getMedia().getData()
            )
        );
    }

    /**
     * 控制逻辑话道录音。
     *
     * @param dto 录音指令参数
     * @return Sidecar 受理结果
     */
    public FNodeResult record(FNodeRecordDTO dto) {
        return sendRequest(
            FNodeMethod.RECORD,
            dto,
            stableCommandId("record", dto.getCtrlUuid(), dto.getUuid(), dto.getAction(), dto.getPath())
        );
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
        return sendRequest(FNodeMethod.HANGUP, dto, stableCommandId("hangup", ctrlUuid, uuid, cause));
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
            stableCommandId("native", cmd, args)
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
        return sendRequest(
            FNodeMethod.TRANSFER,
            params,
            stableCommandId(
                "transfer",
                params.getCtrlUuid(),
                params.getUuid(),
                params.getTarget(),
                params.getContext()
            )
        );
    }

    /**
     * 为同一业务副作用生成稳定命令标识，确保超时查询或重试不会再次执行拨号/桥接。
     *
     * @param operation 业务动作
     * @param parts 动作边界参数
     * @return 稳定命令标识
     */
    private String stableCommandId(String operation, Object... parts) {
        StringBuilder input = new StringBuilder(operation);
        for (Object part : parts) {
            input.append('|').append(part == null ? "" : part);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("cmd-");
            for (int index = 0; index < 16; index++) {
                hex.append(String.format("%02x", digest[index]));
            }
            return hex.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("无法生成稳定命令标识", failure);
        }
    }

    /**
     * 先提交命令意图，再发送逻辑命令并记录同步受理或通信未知。
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
        } catch (Exception invalidRequest) {
            throw new IllegalArgumentException("无法序列化 FNode 指令", invalidRequest);
        }
        recordIntent(commandId, method, payload);
        Message reply;
        try {
            log.debug("[FCC -> NATS] subject={}, method={}, commandId={}", subject, method.getWireName(), commandId);
            reply = natsConnection.request(
                subject,
                payload,
                Duration.ofMillis(fccProperties.getRpcTimeoutMillis())
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            recordUnknown(commandId, "RPC_INTERRUPTED");
            return unknownResult("Interrupted");
        } catch (Exception failure) {
            log.warn("[FCC] RPC 通信结果未知 method={} commandId={} type={}", method.getWireName(), commandId, failure.getClass().getSimpleName());
            recordUnknown(commandId, "RPC_TRANSPORT_ERROR");
            return unknownResult("RPC transport outcome unknown");
        }
        if (reply == null) {
            log.warn("[FCC] RPC 超时 method={} commandId={}", method.getWireName(), commandId);
            recordUnknown(commandId, "RPC_TIMEOUT");
            return unknownResult("RPC timeout");
        }
        try {
            String responsePayload = new String(reply.getData(), StandardCharsets.UTF_8);
            JsonRpcResponse response = objectMapper.readValue(responsePayload, JsonRpcResponse.class);
            FNodeResult result = toResult(response);
            recordReceipt(commandId, responsePayload, result);
            return result;
        } catch (JsonProcessingException invalidReply) {
            recordUnknown(commandId, "RPC_INVALID_REPLY");
            return unknownResult("Invalid RPC response");
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
     * 在发起 NATS 请求之前提交本地意图，失败时禁止产生远程副作用。
     *
     * @param commandId 命令标识
     * @param method FNode 方法
     * @param requestPayload 请求报文
     */
    private void recordIntent(String commandId, FNodeMethod method, byte[] requestPayload) {
        if (callPersistenceService == null) {
            return;
        }
        callPersistenceService.recordCommand(
            CallCommandEntity.builder()
                .commandId(commandId)
                .idempotencyKey(commandId)
                .methodName(method.getWireName())
                .requestPayload(new String(requestPayload, StandardCharsets.UTF_8))
                .build()
        );
    }

    /**
     * 持久化同步 RPC 受理或拒绝，但不能覆盖已到达的最终指令结果。
     *
     * @param commandId 稳定命令标识
     * @param responsePayload 同步应答
     * @param result 受理或拒绝结果
     */
    private void recordReceipt(String commandId, String responsePayload, FNodeResult result) {
        if (callPersistenceService == null) {
            return;
        }
        boolean accepted = result != null && result.isSuccess();
        callPersistenceService.recordCommandReceipt(
            commandId,
            result == null ? null : result.getNodeId(),
            accepted ? "ACCEPTED" : "FAILED",
            responsePayload,
            accepted || result == null ? null : String.valueOf(result.getCode()),
            accepted || result == null ? null : result.getMessage()
        );
    }

    /**
     * 保留通信结果未知状态，不能把超时当成副作用执行失败。
     *
     * @param commandId 稳定命令标识
     * @param reason 不包含敏感信息的未知原因
     */
    private void recordUnknown(String commandId, String reason) {
        if (callPersistenceService != null) {
            callPersistenceService.recordCommandReceipt(commandId, null, "UNKNOWN", null, reason, null);
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
