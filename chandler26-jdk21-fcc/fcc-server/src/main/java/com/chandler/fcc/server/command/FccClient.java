package com.chandler.fcc.server.command;

import com.chandler.fcc.common.dto.command.*;
import com.chandler.fcc.common.dto.rpc.JsonRpcRequest;
import com.chandler.fcc.common.dto.rpc.JsonRpcResponse;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.infrastructure.nats.FccProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * FCC 核心呼叫控制客户端
 * <p>
 * 负责组装标准 JSON-RPC 2.0 请求报文，并通过 NATS 消息总线向部署在 FreeSWITCH 节点侧的 Go Sidecar Agent
 * 投递控制指令（主题格式: fs.cmd.{nodeId}），同步阻塞等待处理结果。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FccClient {

    private final Connection natsConnection;
    private final FccProperties fccProperties;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService callPersistenceService;

    /**
     * 发起外呼呼叫 (FNode.Dial) - 使用默认节点
     *
     * @param dto 外呼指令参数
     * @return FNode 执行结果
     */
    public FNodeResult dial(FNodeDialDTO dto) {
        return dial(fccProperties.getDefaultNodeId(), dto);
    }

    /**
     * 发起外呼呼叫 (FNode.Dial) - 指定节点
     *
     * @param nodeId 软交换节点标识符
     * @param dto    外呼指令参数
     * @return FNode 执行结果
     */
    public FNodeResult dial(String nodeId, FNodeDialDTO dto) {
        String uuid = dto.getUuid();
        if ((uuid == null || uuid.isBlank()) && dto.getDestination()!=null && dto.getDestination().getCallParams()!=null && !dto.getDestination().getCallParams().isEmpty()) {
            uuid = dto.getDestination().getCallParams().getFirst().getUuid();
        }
        if (uuid == null || uuid.isBlank()) { uuid = IdUtil.getUuid(); dto.setUuid(uuid); }
        return sendRequest(nodeId, "FNode.Dial", dto, "dial-" + uuid);
    }

    /**
     * 双向话道桥接 (FNode.ChannelBridge) - 使用默认节点
     *
     * @param ctrlUuid 控制流程标识
     * @param uuid     主话道 UUID
     * @param peerUuid 对端话道 UUID
     * @return FNode 执行结果
     */
    public FNodeResult channelBridge(String ctrlUuid, String uuid, String peerUuid) {
        return channelBridge(fccProperties.getDefaultNodeId(), ctrlUuid, uuid, peerUuid);
    }

    /**
     * 双向话道桥接 (FNode.ChannelBridge) - 指定节点
     *
     * @param nodeId   软交换节点标识符
     * @param ctrlUuid 控制流程标识
     * @param uuid     主话道 UUID
     * @param peerUuid 对端话道 UUID
     * @return FNode 执行结果
     */
    public FNodeResult channelBridge(String nodeId, String ctrlUuid, String uuid, String peerUuid) {
        FNodeBridgeDTO dto = FNodeBridgeDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .peerUuid(peerUuid)
                .build();
        return sendRequest(nodeId, "FNode.ChannelBridge", dto);
    }

    /**
     * DTMF 按键收号 (FNode.ReadDTMF) - 使用默认节点
     *
     * @param dto 按键收号指令参数
     * @return FNode 执行结果
     */
    public FNodeResult readDTMF(FNodeReadDTMFDTO dto) {
        return readDTMF(fccProperties.getDefaultNodeId(), dto);
    }

    /**
     * DTMF 按键收号 (FNode.ReadDTMF) - 指定节点
     *
     * @param nodeId 软交换节点标识符
     * @param dto    按键收号指令参数
     * @return FNode 执行结果
     */
    public FNodeResult readDTMF(String nodeId, FNodeReadDTMFDTO dto) {
        return sendRequest(nodeId, "FNode.ReadDTMF", dto);
    }

    /** 使用持久阶段命令标识放音收号，未知结果不换 ID 重发。
     * @param nodeId 节点 @param dto 动作参数 @param commandId 稳定命令标识 @return 同步受理结果
     */
    public FNodeResult readDTMF(String nodeId, FNodeReadDTMFDTO dto, String commandId) {
        return sendRequest(nodeId, "FNode.ReadDTMF", dto, commandId);
    }

    /**
     * 放音语音播报 (FNode.Play) - 使用默认节点
     *
     * @param dto 放音指令参数
     * @return FNode 执行结果
     */
    public FNodeResult play(FNodePlayDTO dto) {
        return play(fccProperties.getDefaultNodeId(), dto);
    }

    /**
     * 放音语音播报 (FNode.Play) - 指定节点
     *
     * @param nodeId 软交换节点标识符
     * @param dto    放音指令参数
     * @return FNode 执行结果
     */
    public FNodeResult play(String nodeId, FNodePlayDTO dto) {
        return sendRequest(nodeId, "FNode.Play", dto);
    }

    /**
     * 通道录音控制 (FNode.Record) - 使用默认节点
     *
     * @param dto 录音指令参数
     * @return FNode 执行结果
     */
    public FNodeResult record(FNodeRecordDTO dto) {
        return record(fccProperties.getDefaultNodeId(), dto);
    }

    /**
     * 通道录音控制 (FNode.Record) - 指定节点
     *
     * @param nodeId 软交换节点标识符
     * @param dto    录音指令参数
     * @return FNode 执行结果
     */
    public FNodeResult record(String nodeId, FNodeRecordDTO dto) {
        return sendRequest(nodeId, "FNode.Record", dto);
    }

    /**
     * 挂机拆线 (FNode.Hangup) - 使用默认节点
     *
     * @param ctrlUuid 控制流程标识
     * @param uuid     目标通道 UUID
     * @param cause    挂机原因码
     * @return FNode 执行结果
     */
    public FNodeResult hangup(String ctrlUuid, String uuid, String cause) {
        return hangup(fccProperties.getDefaultNodeId(), ctrlUuid, uuid, cause);
    }

    /**
     * 挂机拆线 (FNode.Hangup) - 指定节点
     *
     * @param nodeId   软交换节点标识符
     * @param ctrlUuid 控制流程标识
     * @param uuid     目标通道 UUID
     * @param cause    挂机原因码
     * @return FNode 执行结果
     */
    public FNodeResult hangup(String nodeId, String ctrlUuid, String uuid, String cause) {
        FNodeHangupDTO dto = FNodeHangupDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(uuid)
                .cause(cause != null ? cause : "NORMAL_CLEARING")
                .build();
        return sendRequest(nodeId, "FNode.Hangup", dto);
    }

    /**
     * 透传执行 FreeSWITCH 原生 Native API (FNode.NativeAPI) - 使用默认节点
     *
     * @param cmd  原生指令命令名（如 status, reloadxml, originate）
     * @param args 指令参数字符串
     * @return FNode 执行结果
     */
    public FNodeResult nativeAPI(String cmd, String args) {
        return nativeAPI(fccProperties.getDefaultNodeId(), cmd, args);
    }

    /**
     * 透传执行 FreeSWITCH 原生 Native API (FNode.NativeAPI) - 指定节点
     *
     * @param nodeId 软交换节点标识符
     * @param cmd    原生指令命令名
     * @param args   指令参数字符串
     * @return FNode 执行结果
     */
    public FNodeResult nativeAPI(String nodeId, String cmd, String args) {
        Map<String, String> params = new HashMap<>();
        params.put("cmd", cmd);
        params.put("args", args);
        return sendRequest(nodeId, "FNode.NativeAPI", params);
    }

    /**
     * 查询节点运行状态与健康探活 (FNode.Status) - 使用默认节点
     *
     * @return FNode 执行结果
     */
    public FNodeResult status() {
        return status(fccProperties.getDefaultNodeId());
    }

    /**
     * 查询节点运行状态与健康探活 (FNode.Status) - 指定节点
     *
     * @param nodeId 软交换节点标识符
     * @return FNode 执行结果
     */
    public FNodeResult status(String nodeId) {
        return sendRequest(nodeId, "FNode.Status", null);
    }

    /**
     * 发送同步 JSON-RPC 2.0 请求至指定软交换节点指令主题 (fs.cmd.{nodeId})
     *
     * @param nodeId 软交换节点标识符
     * @param method 远程调用方法名称 (如 FNode.Dial)
     * @param params 业务入参载荷
     * @return FNodeResult 标准执行结果
     */
    private FNodeResult sendRequest(String nodeId, String method, Object params) {
        return sendRequest(nodeId, method, params, IdUtil.getCommandId());
    }

    /** 查询已持久记录的命令应答，不重放副作用。
     * @param nodeId 节点 @param commandId 原命令标识 @return 已受理/失败/未知结果
     */
    public FNodeResult commandResult(String nodeId, String commandId) {
        return sendRequest(nodeId,"FNode.CommandResult",Map.of("command_id",commandId));
    }

    /** 查询节点当前完整话道集合，失败必须视为未知而非空节点。
     * @param nodeId 话道所属节点
     * @return 含 complete/channel_uuids 的结构化结果
     */
    public FNodeResult channelSnapshot(String nodeId) {
        return sendRequest(nodeId, "FNode.ChannelSnapshot", Map.of());
    }

    /** 使用稳定标识发送命令。
     * @param nodeId 节点 @param method 方法 @param params 参数 @param reqId 幂等命令标识 @return 节点应答
     */
    private FNodeResult sendRequest(String nodeId, String method, Object params, String reqId) {
        String effectiveNodeId = (nodeId != null && !nodeId.trim().isEmpty())
                ? nodeId.trim()
                : fccProperties.getDefaultNodeId();

        JsonRpcRequest req = JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(reqId)
                .method(method)
                .params(params)
                .build();

        String subject = "fs.cmd." + effectiveNodeId;
        try {
            byte[] payload = objectMapper.writeValueAsBytes(req);
            log.debug("📤 [FCC -> NATS] 发送指令 Subject: {}, Method: {}\nPayload: {}",
                    subject, method, new String(payload, StandardCharsets.UTF_8));

            Message reply = natsConnection.request(subject, payload, Duration.ofMillis(fccProperties.getRpcTimeoutMillis()));
            if (reply == null) {
                log.error("❌ [FCC] RPC 请求超时 ({} ms), Node: {}, Method: {}",
                        fccProperties.getRpcTimeoutMillis(), effectiveNodeId, method);
                return FNodeResult.builder()
                        .code(-32000)
                        .message("RPC timeout")
                        .nodeId(effectiveNodeId)
                        .build();
            }

            String respStr = new String(reply.getData(), StandardCharsets.UTF_8);
            log.debug("📥 [NATS -> FCC] 收到应答: {}", respStr);

            JsonRpcResponse resp = objectMapper.readValue(respStr, JsonRpcResponse.class);
            if (resp.getError() != null) {
                log.warn("⚠️ [FCC] 节点返回业务错误: code={}, msg={}",
                        resp.getError().getCode(), resp.getError().getMessage());
                return FNodeResult.builder()
                        .code(resp.getError().getCode())
                        .message(resp.getError().getMessage())
                        .data(resp.getError().getData())
                        .nodeId(effectiveNodeId)
                        .build();
            }

            FNodeResult result = resp.getResult();
            if (result != null && result.getNodeId() == null) {
                result.setNodeId(effectiveNodeId);
            }

            if (callPersistenceService != null) {
                try {
                    callPersistenceService.recordCommand(com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity.builder()
                            .commandId(reqId)
                            .idempotencyKey(reqId)
                            .targetNodeId(effectiveNodeId)
                            .methodName(method)
                            .requestPayload(new String(payload, StandardCharsets.UTF_8))
                            .responsePayload(respStr)
                            .status(result != null && (result.getCode() == 0 || result.getCode() == 200) ? "ACCEPTED" : "FAILED")
                            .sentAt(java.time.LocalDateTime.now())
                            .completedAt(java.time.LocalDateTime.now())
                            .build());
                } catch (Exception auditEx) {
                    log.debug("忽略指令审计记录异常: {}", auditEx.getMessage());
                }
            }

            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ [FCC] 请求被中断: {}", e.getMessage());
            return FNodeResult.builder().code(-32000).message("Interrupted").nodeId(effectiveNodeId).build();
        } catch (Exception e) {
            log.error("❌ [FCC] 请求通信异常: {}", e.getMessage(), e);
            return FNodeResult.builder().code(-32000).message(e.getMessage()).nodeId(effectiveNodeId).build();
        }
    }
}
