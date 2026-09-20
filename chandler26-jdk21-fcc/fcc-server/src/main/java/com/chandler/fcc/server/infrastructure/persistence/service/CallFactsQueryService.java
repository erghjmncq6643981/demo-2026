package com.chandler.fcc.server.infrastructure.persistence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallRecordingMapper;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 话务弹屏事实查询服务
 * <p>
 * 为坐席弹屏提供<b>只读</b>的真实事实来源，不含任何示例/兜底数据：
 * </p>
 * <ul>
 *   <li>同号码前序通话 (防撞单)：查 {@code fcc_call_session}，坐席与姓名取自该表真实落库的 {@code agent_work_no/agent_name}；</li>
 *   <li>前序录音复播地址：查 {@code fcc_call_recording}，仅当存在带 {@code object_key} 的录音元数据时才给出；</li>
 *   <li>坐席姓名：查 {@code fcc_agent} (管理端主数据，只读投影)。</li>
 * </ul>
 *
 * @author Chandler
 * @since 2026-09-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallFactsQueryService {

    private final CallSessionMapper callSessionMapper;
    private final CallRecordingMapper callRecordingMapper;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * 主叫号码的前序通话事实
     *
     * @param agentName     前序接待坐席姓名 (可能为空)
     * @param agentWorkNo   前序接待坐席工号 (可能为空)
     * @param startedAt     前序通话开始时间
     * @param talkDurationMs 前序通话通话时长 (毫秒，可能为空)
     * @param result        前序通话挂机结果码
     * @param callId        前序通话数值主键
     * @param recordingId   前序通话可复播的录音业务标识；无录音时为 null
     */
    public record CallerHistory(String agentName, String agentWorkNo, LocalDateTime startedAt,
                                Long talkDurationMs, String result, Long callId, String recordingId) {
    }

    /**
     * 查询同号码最近一次<b>已结束</b>的通话
     * <p>
     * 只取已结束的通话可避免把并发在途的另一通电话误判成"前序记录"；
     * 同时排除当前这通电话自身的会话主键。
     * </p>
     *
     * @param number         客户号码
     * @param tenantId       当前通话租户
     * @param workNo         已授权的当前坐席，仅查询本人接待历史
     * @param matchCallerSide true 按 {@code caller_number} 匹配 (呼入场景，客户是主叫)；
     *                        false 按 {@code destination_number} 匹配 (呼出场景，客户是被叫)
     * @param excludeCallId  需排除的当前通话数值主键 (可为 null)
     * @return 前序通话事实；查不到时返回空
     */
    public Optional<CallerHistory> findLatestHistory(String number, boolean matchCallerSide, Long excludeCallId,
                                                   Long tenantId, String workNo) {
        if (number == null || number.isBlank() || tenantId == null || workNo == null || workNo.isBlank()) {
            return Optional.empty();
        }

        LambdaQueryWrapper<CallSessionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallSessionEntity::getTenantId, tenantId)
                .eq(CallSessionEntity::getPrimaryWorkNo, workNo);
        if (matchCallerSide) {
            wrapper.eq(CallSessionEntity::getCallerNumber, number);
        } else {
            wrapper.eq(CallSessionEntity::getDestinationNumber, number);
        }
        wrapper.isNotNull(CallSessionEntity::getEndedAt);
        if (excludeCallId != null) {
            wrapper.ne(CallSessionEntity::getId, excludeCallId);
        }
        wrapper.orderByDesc(CallSessionEntity::getStartedAt).last("LIMIT 1");

        CallSessionEntity previous;
        try {
            previous = callSessionMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("[弹屏事实] 查询本人前序通话失败 tenantId={} workNo={}", tenantId, workNo);
            return Optional.empty();
        }

        if (previous == null) {
            return Optional.empty();
        }

        CallRecordingEntity recording = findPlayableRecording(previous.getId());
        return Optional.of(new CallerHistory(
                previous.getAgentName(),
                previous.getAgentWorkNo(),
                previous.getStartedAt(),
                previous.getTalkDurationMs(),
                previous.getResult(),
                previous.getId(),
                recording != null ? recording.getRecordingId() : null));
    }

    /**
     * 查询指定通话可复播的录音元数据
     *
     * @param callId 通话数值主键
     * @return 录音元数据；无有效录音时返回 null
     */
    public CallRecordingEntity findPlayableRecording(Long callId) {
        if (callId == null) {
            return null;
        }
        try {
            return callRecordingMapper.selectOne(
                    new LambdaQueryWrapper<CallRecordingEntity>()
                            .eq(CallRecordingEntity::getCallId, callId)
                            .isNotNull(CallRecordingEntity::getObjectKey)
                            .orderByDesc(CallRecordingEntity::getStartedAt)
                            .orderByDesc(CallRecordingEntity::getId)
                            .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("⚠️ [弹屏事实] 查询通话录音元数据失败: callId={}, err={}", callId, e.getMessage());
            return null;
        }
    }

    /**
     * 按坐席工号查询坐席姓名
     *
     * @param workNo 坐席工号
     * @return 坐席姓名；不存在时返回空
     */
    public Optional<String> findAgentName(String workNo) {
        if (workNo == null || workNo.isBlank() || jdbcTemplate == null) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT agent_name FROM fcc_agent WHERE work_no = ? AND deleted_at IS NULL LIMIT 1",
                    workNo);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            Object name = rows.getFirst().get("agent_name");
            return name == null ? Optional.empty() : Optional.of(String.valueOf(name));
        } catch (Exception e) {
            log.warn("⚠️ [弹屏事实] 查询坐席姓名失败: workNo={}, err={}", workNo, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 按坐席工号解析其绑定终端分机
     * <p>
     * 优先取坐席当前使用分机 {@code current_extension}，其次取启用的 SIP 终端绑定值。
     * </p>
     *
     * @param workNo 坐席工号
     * @return 分机号；未绑定终端时返回空
     */
    public Optional<String> findAgentExtension(String workNo) {
        if (workNo == null || workNo.isBlank() || jdbcTemplate == null) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT a.current_extension, b.endpoint_type, b.endpoint_value " +
                            "FROM fcc_agent a " +
                            "LEFT JOIN fcc_agent_endpoint_binding b ON a.id = b.agent_id AND b.status = 'ENABLED' " +
                            "WHERE a.work_no = ? AND a.deleted_at IS NULL LIMIT 1",
                    workNo);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> row = rows.getFirst();
            Object currentExtension = row.get("current_extension");
            if (currentExtension != null && !String.valueOf(currentExtension).isBlank()) {
                return Optional.of(String.valueOf(currentExtension));
            }
            Object endpointValue = row.get("endpoint_value");
            if (endpointValue != null && !String.valueOf(endpointValue).isBlank()) {
                return Optional.of(String.valueOf(endpointValue));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("⚠️ [弹屏事实] 查询坐席终端失败: workNo={}, err={}", workNo, e.getMessage());
            return Optional.empty();
        }
    }
}
