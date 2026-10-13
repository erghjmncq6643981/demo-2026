package com.chandler.fcc.server.command.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallCommandMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 独立维护命令意图、受理与最终结果；事务语义与原公开持久化入口一致。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommandAuditPersistence {
    private final CallCommandMapper callCommandMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 在远程调用之前持久化命令意图；稳定 ID 重试只能使用完全相同的请求。
     *
     * @param command 待发送的指令意图
     * @throws IllegalStateException 稳定标识被不同请求复用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordCommand(CallCommandEntity command) {
        if (command == null || command.getCommandId() == null || command.getCommandId().isBlank()) {
            throw new IllegalArgumentException("命令意图和 command_id 不能为空");
        }
        CallCommandEntity existing = findCommand(command.getCommandId());
        if (existing != null) {
            verifySameCommand(existing, command);
            return;
        }
        if (command.getId() == null) {
            command.setId(IdUtil.nextId());
        }
        if (command.getIdempotencyKey() == null) {
            command.setIdempotencyKey(command.getCommandId());
        }
        if (command.getCreatedAt() == null) {
            command.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        command.setStatus("CREATED");
        try {
            callCommandMapper.insert(command);
        } catch (DataIntegrityViolationException duplicate) {
            existing = findCommand(command.getCommandId());
            if (existing == null) {
                throw duplicate;
            }
            verifySameCommand(existing, command);
        }
        log.debug("[指令意图] method={} commandId={}", command.getMethodName(), command.getCommandId());
    }

    /**
     * 记录同步受理或通信未知，但不覆盖先于 RPC 应答到达的最终结果。
     *
     * @param commandId 稳定命令标识
     * @param nodeId Sidecar 实际节点，可为空
     * @param status ACCEPTED、FAILED 或 UNKNOWN
     * @param responsePayload 同步 JSON-RPC 应答，可为空
     * @param errorCode 错误代码，可为空
     * @param errorMessage 不包含敏感信息的错误说明，可为空
     * @return 更新行数；最终结果已先到达时返回零
     */
    @Transactional(rollbackFor = Exception.class)
    public int recordCommandReceipt(
        String commandId,
        String nodeId,
        String status,
        String responsePayload,
        String errorCode,
        String errorMessage
    ) {
        LambdaUpdateWrapper<CallCommandEntity> update = new LambdaUpdateWrapper<CallCommandEntity>()
            .eq(CallCommandEntity::getCommandId, commandId)
            .in(CallCommandEntity::getStatus, "CREATED", "SENT", "ACCEPTED", "UNKNOWN")
            .set(CallCommandEntity::getStatus, status)
            .set(CallCommandEntity::getErrorCode, errorCode)
            .set(CallCommandEntity::getErrorMessage, errorMessage)
            .set(CallCommandEntity::getSentAt, LocalDateTime.now(ZoneOffset.UTC));
        if (nodeId != null) {
            update.set(CallCommandEntity::getAssignedNodeId, nodeId);
        }
        if (responsePayload != null) {
            update.set(CallCommandEntity::getResponsePayload, responsePayload);
        }
        if ("FAILED".equals(status)) {
            update.set(CallCommandEntity::getCompletedAt, LocalDateTime.now(ZoneOffset.UTC));
        }
        return callCommandMapper.update(null, update);
    }

    /**
     * 根据唯一命令标识读取本地审计。
     *
     * @param commandId 稳定命令标识
     * @return 审计记录，尚未插入时为空
     */
    private CallCommandEntity findCommand(String commandId) {
        return callCommandMapper.selectOne(
            new LambdaQueryWrapper<CallCommandEntity>()
                .eq(CallCommandEntity::getCommandId, commandId)
        );
    }

    /**
     * 拒绝同一命令标识对应不同副作用，避免幂等重试变成另一条命令。
     *
     * @param existing 已持久化命令
     * @param incoming 本次重试命令
     * @throws IllegalStateException 方法或请求内容不同
     */
    private void verifySameCommand(CallCommandEntity existing, CallCommandEntity incoming) {
        if (
            !Objects.equals(existing.getMethodName(), incoming.getMethodName()) ||
            !Objects.equals(existing.getRequestPayload(), incoming.getRequestPayload())
        ) {
            throw new IllegalStateException("稳定 command_id 被不同指令请求复用");
        }
    }

    /**
     * 使用 Sidecar 发布的最终结果完成既有指令审计。
     *
     * <p>同步 JSON-RPC 应答只代表 Sidecar 已受理；本方法只由
     * {@code Event.CommandResult} 调用，将命令推进为最终成功或失败状态。</p>
     *
     * @param commandId 原始稳定指令标识
     * @param methodName 指令方法，用于拒绝同一 ID 上的其他命令结果
     * @param assignedNodeId 实际执行指令的 Sidecar 节点标识
     * @param controlId 结果中的控制标识
     * @param channelUuid 结果中的话道标识
     * @param status 指令最终状态
     * @param errorCode 失败代码，成功时可为空
     * @param message 指令结果说明
     * @param responsePayload Sidecar 发布的规范结果载荷
     * @return 实际更新的指令记录数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int completeCommand(
        String commandId,
        String methodName,
        String assignedNodeId,
        String controlId,
        String channelUuid,
        String status,
        String errorCode,
        String message,
        String responsePayload
    ) {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("完成指令审计时 command_id 不能为空");
        }
        CallCommandEntity command = findCommand(commandId);
        if (command == null) {
            return 0;
        }
        if (!Objects.equals(command.getMethodName(), methodName)) {
            throw new IllegalArgumentException("指令结果方法与原命令不符");
        }
        verifyCommandCorrelation(command, controlId, channelUuid);
        if (
            command.getAssignedNodeId() != null &&
            !Objects.equals(command.getAssignedNodeId(), assignedNodeId)
        ) {
            throw new IllegalArgumentException("指令结果来源节点与受理节点不符");
        }
        int updated = callCommandMapper.update(
            null,
            new LambdaUpdateWrapper<CallCommandEntity>()
                .eq(CallCommandEntity::getCommandId, commandId)
                .eq(CallCommandEntity::getMethodName, methodName)
                .notIn(CallCommandEntity::getStatus, "SUCCESS", "FAILED")
                .set(CallCommandEntity::getAssignedNodeId, assignedNodeId)
                .set(CallCommandEntity::getStatus, status)
                .set(CallCommandEntity::getErrorCode, errorCode)
                .set(CallCommandEntity::getErrorMessage, message)
                .set(CallCommandEntity::getResponsePayload, responsePayload)
                .set(CallCommandEntity::getCompletedAt, LocalDateTime.now(ZoneOffset.UTC))
        );
        if (updated > 0) {
            return updated;
        }
        CallCommandEntity existing = findCommand(commandId);
        if (existing == null || (!"SUCCESS".equals(existing.getStatus()) && !"FAILED".equals(existing.getStatus()))) {
            return 0;
        }
        if (!Objects.equals(existing.getStatus(), status)) {
            throw new IllegalArgumentException("同一指令收到互相冲突的最终状态");
        }
        return 1;
    }

    /**
     * 校验最终结果携带的控制和话道身份与原始 wire 请求一致。
     *
     * @param command 已持久化原命令
     * @param controlId 结果控制标识
     * @param channelUuid 结果话道标识
     * @throws IllegalArgumentException 请求载荷损坏或关联身份不一致
     */
    private void verifyCommandCorrelation(
        CallCommandEntity command,
        String controlId,
        String channelUuid
    ) {
        try {
            var params = objectMapper.readTree(command.getRequestPayload()).path("params");
            String expectedControlId = params.path("ctrl_uuid").asText(null);
            String expectedChannelUuid = params.path("uuid").asText(null);
            if (
                !Objects.equals(expectedControlId, controlId) ||
                !Objects.equals(expectedChannelUuid, channelUuid)
            ) {
                throw new IllegalArgumentException("指令结果控制或话道身份与原命令不符");
            }
        } catch (JsonProcessingException invalidPayload) {
            throw new IllegalArgumentException("原命令审计载荷损坏", invalidPayload);
        }
    }

}
