package com.chandler.fcc.server.infrastructure.persistence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.infrastructure.persistence.entity.*;
import com.chandler.fcc.server.infrastructure.persistence.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 通话领域数据持久化综合服务
 * <p>
 * 负责话务会话 (fcc_call_session)、话道 Leg (fcc_call_leg)、原始事件 (fcc_call_event)、
 * 控制指令审计 (fcc_call_command)、媒体桥接 (fcc_call_bridge) 与录音元数据 (fcc_call_recording)
 * 的生命周期落盘与聚合持久化。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallPersistenceService {

    private final CallSessionMapper callSessionMapper;
    private final CallLegMapper callLegMapper;
    private final CallEventMapper callEventMapper;
    private final CallCommandMapper callCommandMapper;
    private final CallRecordingMapper callRecordingMapper;
    private final CallBridgeMapper callBridgeMapper;
    private final CallBridgeMemberMapper callBridgeMemberMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存或更新通话会话聚合根
     *
     * @param callInfo 通话业务对象 BO
     * @return 持久化后的会话实体
     */
    @Transactional(rollbackFor = Exception.class)
    public CallSessionEntity saveOrUpdateSession(CallInfoBO callInfo) {
        if (callInfo == null || callInfo.getCtrlId() == null) {
            return null;
        }

        Long numericCallId = parseNumericId(callInfo.getCallId());

        CallSessionEntity existing = callSessionMapper.selectOne(
                new LambdaQueryWrapper<CallSessionEntity>()
                        .eq(CallSessionEntity::getCtrlId, callInfo.getCtrlId()).last("FOR UPDATE")
        );

        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);

        if (existing == null) {
            CallSessionEntity entity = CallSessionEntity.builder()
                    .id(numericCallId)
                    .tenantId(callInfo.getData().get("tenantId") instanceof Number tenant ? tenant.longValue() : 0L)
                    .bizId(callInfo.getDataStr("dialJobId",callInfo.getCallId()))
                    .ctrlId(callInfo.getCtrlId())
                    .modelType(callInfo.getModelKey() != null ? callInfo.getModelKey() : "UNKNOWN")
                    .direction(callInfo.getDirection() != null ? callInfo.getDirection().name() : "OUTBOUND")
                    .callerNumber(callInfo.getCallerNumber() != null ? callInfo.getCallerNumber() : "")
                    .destinationNumber(callInfo.getDestinationNumber() != null ? callInfo.getDestinationNumber() : "")
                    .status(callInfo.getStageState() != null ? callInfo.getStageState().name() : "START")
                    .result(callInfo.getHangupCause())
                    .startedAt(now)
                    .ringingAt(now)
                    .primaryWorkNo(callInfo.getAgentWorkNo())
                    .agentWorkNo(callInfo.getAgentWorkNo())
                    .agentName(callInfo.getDataStr("agentName", null))
                    .evaluationScore(callInfo.getEvaluationScore())
                    .version(0L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            try {
                if (callInfo.getData() != null && !callInfo.getData().isEmpty()) {
                    entity.setAttributes(objectMapper.writeValueAsString(callInfo.getData()));
                }
            } catch (Exception ignored) {}

            callSessionMapper.insert(entity);
            log.info("💾 [持久化] 成功新建通话会话记录: id={}, ctrlId={}", entity.getId(), entity.getCtrlId());
            return entity;
        } else {
            if (callInfo.getStageState() != null) {
                // 如果通话已经挂机结束，绝不倒退回 CALLING / RINGING 状态
                if (existing.getEndedAt() == null || callInfo.getStageState() == CallStageState.NORMAL_END) {
                    existing.setStatus(callInfo.getStageState().name());
                }
            }
            if (callInfo.getHangupCause() != null) {
                existing.setHangupCause(callInfo.getHangupCause());
                existing.setResult(callInfo.getHangupCause());
            }
            if (callInfo.getEvaluationScore() != null) {
                existing.setEvaluationScore(callInfo.getEvaluationScore());
            }
            // 接待坐席事实：坐席路由确定后才会有值，一旦落库即作为"前序接待人"的事实来源
            if (callInfo.getAgentWorkNo() != null && !callInfo.getAgentWorkNo().isBlank()) {
                existing.setAgentWorkNo(callInfo.getAgentWorkNo());
                existing.setPrimaryWorkNo(callInfo.getAgentWorkNo());
                String agentName = callInfo.getDataStr("agentName", null);
                if (agentName != null && !agentName.isBlank()) {
                    existing.setAgentName(agentName);
                }
            }
            if (callInfo.getDuration() != null) {
                existing.setTotalDurationMs(callInfo.getDuration() * 1000L);
            }
            if (callInfo.getBillsec() != null) {
                existing.setTalkDurationMs(callInfo.getBillsec() * 1000L);
            }
            if (("CONNECTED".equalsIgnoreCase(existing.getStatus()) || "ANSWERED".equalsIgnoreCase(existing.getStatus())) && existing.getAnsweredAt() == null) {
                existing.setAnsweredAt(now);
            }
            if ("NORMAL_END".equalsIgnoreCase(existing.getStatus()) || "ERROR_END".equalsIgnoreCase(existing.getStatus()) || callInfo.getStageState() == CallStageState.NORMAL_END) {
                if (existing.getEndedAt() == null) {
                    existing.setEndedAt(now);
                }
                // 终态如实保留生命周期状态，不覆盖成接听结果：
                // status 表达"通话如何结束"（NORMAL_END / ERROR_END），result 表达挂机结果码
                // （FreeSWITCH cause，如 NORMAL_CLEARING），两者语义不同、不可互相顶替。
                // 话单层"已接听 / 未接听"的归一化由查询侧依据 answeredAt 与通话时长推导，
                // 不应回写事实表，否则事后无法区分正常结束与异常结束。
                if (!"NORMAL_END".equalsIgnoreCase(existing.getStatus())
                        && !"ERROR_END".equalsIgnoreCase(existing.getStatus())) {
                    existing.setStatus(callInfo.getStageState() != null
                            ? callInfo.getStageState().name()
                            : "NORMAL_END");
                }
            }

            try {
                if (callInfo.getData() != null && !callInfo.getData().isEmpty()) {
                    var attributes = new java.util.HashMap<String,Object>(callInfo.getData());
                    // 话后小结由独立事务保存，迟到话务事件不能覆盖已提交的业务结果。
                    if (existing.getAttributes() != null) {
                        var saved = objectMapper.readTree(existing.getAttributes()).get("afterCall");
                        if (saved != null) attributes.put("afterCall", saved);
                    }
                    existing.setAttributes(objectMapper.writeValueAsString(attributes));
                }
            } catch (Exception ignored) {}

            existing.setUpdatedAt(now);
            callSessionMapper.updateById(existing);
            log.debug("💾 [持久化] 成功更新通话会话记录: id={}, status={}", existing.getId(), existing.getStatus());
            return existing;
        }
    }

    /**
     * 保存或更新话道 Leg 事实实体
     *
     * @param leg 话道实体
     * @return 持久化后的实体
     */
    @Transactional(rollbackFor = Exception.class)
    public CallLegEntity saveOrUpdateLeg(CallLegEntity leg) {
        if (leg == null || leg.getChannelUuid() == null) {
            return null;
        }

        CallLegEntity existing = callLegMapper.selectOne(
                new LambdaQueryWrapper<CallLegEntity>()
                        .eq(CallLegEntity::getChannelUuid, leg.getChannelUuid()).last("FOR UPDATE")
        );

        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (existing == null) {
            if (leg.getId() == null) {
                leg.setId(IdUtil.nextId());
            }
            if (leg.getTenantId() == null) {
                leg.setTenantId(0L);
            }
            if (leg.getCreatedTime() == null) {
                leg.setCreatedTime(now);
            }
            leg.setCreatedAt(now);
            leg.setUpdatedAt(now);
            leg.setVersion(0L);
            callLegMapper.insert(leg);
            log.info("💾 [持久化] 成功新建话道 Leg: id={}, uuid={}, role={}", leg.getId(), leg.getChannelUuid(), leg.getRoleType());
            return leg;
        } else {
            if (com.chandler.fcc.server.call.LegStatePolicy.accepts(existing.getState(), leg.getState())) existing.setState(leg.getState());
            if (leg.getAnsweredAt() != null && existing.getAnsweredAt() == null) existing.setAnsweredAt(leg.getAnsweredAt());
            if (leg.getBridgedAt() != null && existing.getBridgedAt() == null) existing.setBridgedAt(leg.getBridgedAt());
            if (leg.getEndedAt() != null && existing.getEndedAt() == null) existing.setEndedAt(leg.getEndedAt());
            if (leg.getHangupCause() != null) existing.setHangupCause(leg.getHangupCause());
            if (leg.getTalkDurationMs() != null) existing.setTalkDurationMs(leg.getTalkDurationMs());
            existing.setUpdatedAt(now);
            callLegMapper.updateById(existing);
            return existing;
        }
    }

    /**
     * 写入话务原始事件审计
     *
     * @param event 事件实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordEvent(CallEventEntity event) {
        if (event == null) {
            return;
        }
        if (event.getId() == null) {
            event.setId(IdUtil.nextId());
        }
        if (event.getTenantId() == null) {
            event.setTenantId(0L);
        }
        if (event.getEventId() == null) {
            event.setEventId(IdUtil.getEventId());
        }
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now());
        }
        if (event.getReceivedAt() == null) {
            event.setReceivedAt(LocalDateTime.now());
        }
        if (event.getProcessStatus() == null) {
            event.setProcessStatus("PROCESSED");
        }
        callEventMapper.insert(event);
        log.debug("💾 [持久化] 记录事件: type={}, id={}", event.getEventType(), event.getEventId());
    }

    /**
     * 写入控制指令审计
     *
     * @param command 指令实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordCommand(CallCommandEntity command) {
        if (command == null) {
            return;
        }
        if (command.getId() == null) {
            command.setId(IdUtil.nextId());
        }
        if (command.getTenantId() == null) {
            command.setTenantId(0L);
        }
        if (command.getCommandId() == null) {
            command.setCommandId(IdUtil.getCommandId());
        }
        if (command.getIdempotencyKey() == null) {
            command.setIdempotencyKey(command.getCommandId());
        }
        if (command.getCreatedAt() == null) {
            command.setCreatedAt(LocalDateTime.now());
        }
        callCommandMapper.insert(command);
        log.debug("💾 [持久化] 记录指令: method={}, id={}", command.getMethodName(), command.getCommandId());
    }

    /**
     * 写入录音元数据
     *
     * @param recording 录音实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordRecording(CallRecordingEntity recording) {
        if (recording == null) {
            return;
        }
        if (recording.getId() == null) {
            recording.setId(IdUtil.nextId());
        }
        if (recording.getTenantId() == null) {
            recording.setTenantId(0L);
        }
        if (recording.getRecordingId() == null) {
            recording.setRecordingId("rec-" + IdUtil.nextIdStr());
        }
        LocalDateTime now = LocalDateTime.now();
        if (recording.getCreatedAt() == null) {
            recording.setCreatedAt(now);
        }
        if (recording.getUpdatedAt() == null) {
            recording.setUpdatedAt(now);
        }
        callRecordingMapper.insert(recording);
        log.info("💾 [持久化] 记录录音: callId={}, path={}", recording.getCallId(), recording.getObjectKey());
    }

    /**
     * 按录音业务唯一标识新增或补齐录音元数据
     * <p>
     * 同一通电话的录音会经历「下发 START 时登记地址」与「收到落盘事件时补齐大小/时长/结束时间」
     * 两个阶段，两次操作共用同一个 {@code recording_id}，因此必须按该键做幂等合并，
     * 不能重复插入导致唯一键冲突或产生两份元数据。
     * </p>
     *
     * @param incoming 本次待写入的录音事实，必须携带 recordingId
     * @return 合并后的录音实体
     */
    @Transactional(rollbackFor = Exception.class)
    public CallRecordingEntity upsertRecording(CallRecordingEntity incoming) {
        if (incoming == null) {
            return null;
        }
        if (incoming.getRecordingId() == null || incoming.getRecordingId().isBlank()) {
            recordRecording(incoming);
            return incoming;
        }

        LocalDateTime now = LocalDateTime.now();
        CallRecordingEntity existing = callRecordingMapper.selectOne(
                new LambdaQueryWrapper<CallRecordingEntity>()
                        .eq(CallRecordingEntity::getRecordingId, incoming.getRecordingId()));

        if (existing == null) {
            incoming.setId(incoming.getId() != null ? incoming.getId() : IdUtil.nextId());
            incoming.setTenantId(incoming.getTenantId() != null ? incoming.getTenantId() : 0L);
            incoming.setCreatedAt(now);
            incoming.setUpdatedAt(now);
            callRecordingMapper.insert(incoming);
            log.info("💾 [持久化] 新建录音元数据: recordingId={}, callId={}, path={}",
                    incoming.getRecordingId(), incoming.getCallId(), incoming.getObjectKey());
            return incoming;
        }

        if (incoming.getCallId() != null) existing.setCallId(incoming.getCallId());
        if (incoming.getLegId() != null) existing.setLegId(incoming.getLegId());
        if (incoming.getBridgeId() != null) existing.setBridgeId(incoming.getBridgeId());
        if (incoming.getNodeId() != null) existing.setNodeId(incoming.getNodeId());
        if (incoming.getStatus() != null) existing.setStatus(incoming.getStatus());
        if (incoming.getStorageType() != null) existing.setStorageType(incoming.getStorageType());
        if (incoming.getObjectKey() != null && !incoming.getObjectKey().isBlank()) {
            existing.setObjectKey(incoming.getObjectKey());
        }
        if (incoming.getMediaFormat() != null) existing.setMediaFormat(incoming.getMediaFormat());
        if (incoming.getSizeBytes() != null) existing.setSizeBytes(incoming.getSizeBytes());
        if (incoming.getDurationMs() != null) existing.setDurationMs(incoming.getDurationMs());
        if (incoming.getChecksum() != null) existing.setChecksum(incoming.getChecksum());
        if (incoming.getStartedAt() != null && existing.getStartedAt() == null) {
            existing.setStartedAt(incoming.getStartedAt());
        }
        if (incoming.getCompletedAt() != null) existing.setCompletedAt(incoming.getCompletedAt());
        if (incoming.getRetainUntil() != null) existing.setRetainUntil(incoming.getRetainUntil());
        existing.setUpdatedAt(now);

        callRecordingMapper.updateById(existing);
        log.info("💾 [持久化] 合并录音元数据: recordingId={}, status={}, size={}",
                existing.getRecordingId(), existing.getStatus(), existing.getSizeBytes());
        return existing;
    }

    /**
     * 查询指定通话的全部录音元数据 (按开始时间倒序)
     *
     * @param callId 通话会话数值主键
     * @return 录音实体列表
     */
    public List<CallRecordingEntity> findRecordingsByCallId(Long callId) {
        if (callId == null) {
            return List.of();
        }
        return callRecordingMapper.selectList(new LambdaQueryWrapper<CallRecordingEntity>()
                .eq(CallRecordingEntity::getCallId, callId)
                .orderByDesc(CallRecordingEntity::getStartedAt)
                .orderByDesc(CallRecordingEntity::getId));
    }

    /**
     * 创建媒体桥接及成员记录
     *
     * @param bridge  桥接实体
     * @param legAId  话道 A 雪花 ID
     * @param legBId  话道 B 雪花 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordBridge(CallBridgeEntity bridge, Long legAId, Long legBId) {
        if (bridge == null) {
            return;
        }
        if (bridge.getId() == null) {
            bridge.setId(IdUtil.nextId());
        }
        if (bridge.getTenantId() == null) {
            bridge.setTenantId(0L);
        }
        if (bridge.getCreatedAt() == null) {
            bridge.setCreatedAt(LocalDateTime.now());
        }
        callBridgeMapper.insert(bridge);

        LocalDateTime now = LocalDateTime.now();
        if (legAId != null) {
            CallBridgeMemberEntity memberA = CallBridgeMemberEntity.builder()
                    .id(IdUtil.nextId())
                    .bridgeId(bridge.getId())
                    .legId(legAId)
                    .joinedAt(bridge.getStartedAt() != null ? bridge.getStartedAt() : now)
                    .createdAt(now)
                    .build();
            callBridgeMemberMapper.insert(memberA);
        }
        if (legBId != null) {
            CallBridgeMemberEntity memberB = CallBridgeMemberEntity.builder()
                    .id(IdUtil.nextId())
                    .bridgeId(bridge.getId())
                    .legId(legBId)
                    .joinedAt(bridge.getStartedAt() != null ? bridge.getStartedAt() : now)
                    .createdAt(now)
                    .build();
            callBridgeMemberMapper.insert(memberB);
        }
        log.info("💾 [持久化] 记录桥接: bridgeUuid={}, legA={}, legB={}", bridge.getBridgeUuid(), legAId, legBId);
    }

    /**
     * 根据控制标识查询会话
     *
     * @param ctrlId 控制流程标识
     * @return 会话实体 Optional
     */
    public Optional<CallSessionEntity> findSessionByCtrlId(String ctrlId) {
        if (ctrlId == null) return Optional.empty();
        return Optional.ofNullable(callSessionMapper.selectOne(
                new LambdaQueryWrapper<CallSessionEntity>().eq(CallSessionEntity::getCtrlId, ctrlId)
        ));
    }

    /**
     * 严格解析纯数字业务 call_id，不生成替代 ID，也不接受旧前缀。
     *
     * @param idStr 业务 ID 字符串
     * @return 64位数值 ID
     * @throws IllegalArgumentException ID 为空、非规范正整数或超出 BIGINT 范围
     */
    public static Long parseNumericId(String idStr) {
        if (idStr == null || !idStr.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("callId 必须为纯数字正整数");
        return Long.parseLong(idStr);
    }
}
