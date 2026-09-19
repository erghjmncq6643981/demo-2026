package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.dto.command.FNodeRecordDTO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.recording.RecordingPathResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通道双轨录音动作执行器 (RECORD)
 * <p>
 * 下发 FreeSWITCH 话道双向录音启停指令，并在<b>下发 START 的同一时刻</b>确定录音文件地址：
 * </p>
 * <ol>
 *   <li>依据 {@code fcc.recording.base-dir} 计算共享存储绝对路径 {@code baseDir/yyyy/MM/dd/{call_id}.wav}；</li>
 *   <li>把该绝对路径随 {@code FNode.Record} 指令下发给 FreeSWITCH，由 {@code uuid_record} 直接写入；</li>
 *   <li>把同一路径登记进 {@code fcc_call_recording.object_key}，使库内地址与磁盘文件一一对应。</li>
 * </ol>
 * <p>
 * 由于该路径对 FreeSWITCH 与控制面是同一份共享挂载，管理端后续可据此直接复播与下载，
 * 不再依赖任何本地演示目录兜底。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class RecordActionExecutor extends AbstractFccActionExecutor {

    /**
     * 录音动作: 开始
     */
    private static final String ACTION_START = "START";

    /**
     * 录音动作: 停止
     */
    private static final String ACTION_STOP = "STOP";

    @Autowired(required = false)
    private RecordingPathResolver recordingPathResolver;

    @Autowired(required = false)
    private CallPersistenceService callPersistenceService;

    /**
     * 返回执行器支持的动作类型。
     * @return 业务动作类型
     */
    @Override
    public ActionType getActionType() {
        return ActionType.RECORD;
    }

    /**
     * 根据明确的运行时标识执行呼叫动作。
     * @param callUuid 业务通话标识，不作为话道或控制标识
     * @param flowUuid 流程步骤实例标识
     * @param flowNode 包含独立控制、话道及动作参数的节点
     * @throws IllegalArgumentException 必需参数缺失时抛出
     */
    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String targetUuid = requiredData(flowNode, "channelUuid");
        String ctrlUuid = requiredData(flowNode, "ctrlId");
        String action = normalizeAction(flowNode.getDataStr("action", ACTION_START));

        // 1. 确定录音地址：流程节点可显式声明 path，否则按共享存储布局自动推导
        String declaredPath = flowNode.getDataStr("path", null);
        String recordPath = resolveRecordPath(callUuid, declaredPath);
        String recordingId = ResolvedPath.recordingIdOf(callUuid);
        String mediaFormat = ResolvedPath.formatOf(recordPath);

        log.info("🎙️ [FCC 执行动作: 通道录音] Action: {}, CallId: {}, CtrlId: {}, TargetUUID: {}, SharedPath: {}",
                action, callUuid, ctrlUuid, targetUuid, recordPath);

        // 2. 下发 FreeSWITCH 录音指令 (path 为 FreeSWITCH 与呼叫中心共享的同一绝对路径)
        FNodeRecordDTO recordDTO = FNodeRecordDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(targetUuid)
                .action(action)
                .path(recordPath)
                .build();

        FNodeResult result = getFccClient().record(recordDTO);
        boolean accepted = result != null && result.getCode() != null && result.getCode() == 200;
        log.info("📥 [FCC 通道录音应答] Action: {}, Accepted: {}, Result: {}", action, accepted, result);

        // 3. 同步登记录音元数据事实 (START 建账，STOP 结账)
        persistRecording(action, callUuid, ctrlUuid, recordingId, recordPath, mediaFormat, accepted);
    }

    /**
     * 计算录音文件地址
     *
     * @param callUuid     业务通话标识
     * @param declaredPath 流程节点显式声明的路径，可为空
     * @return 共享存储绝对路径
     */
    private String resolveRecordPath(String callUuid, String declaredPath) {
        if (recordingPathResolver != null) {
            return recordingPathResolver.resolve(callUuid, java.time.LocalDateTime.now(), declaredPath).absolutePath();
        }
        // 极端场景 (未装配解析器) 下仍保证路径可推导，避免退化为硬编码目录
        if (declaredPath != null && !declaredPath.isBlank()) {
            return declaredPath.trim();
        }
        return ResolvedPath.defaultPathOf(callUuid);
    }

    /**
     * 登记录音元数据
     * <p>
     * START 时以 RECORDING 状态建账，STOP 时结账为 COMPLETED 并刷新地址，
     * 全程以 recording_id 幂等合并，重复事件不会产生重复记录。
     * </p>
     */
    private void persistRecording(String action, String callUuid, String ctrlUuid, String recordingId,
                                  String recordPath, String mediaFormat, boolean accepted) {
        if (callPersistenceService == null) {
            log.warn("⚠️ [录音] 持久化服务未装配，录音地址仅下发未落库: {}", recordPath);
            return;
        }
        try {
            boolean stopping = ACTION_STOP.equals(action);
            CallRecordingEntity entity = CallRecordingEntity.builder()
                    .recordingId(recordingId)
                    .callId(CallPersistenceService.parseNumericId(callUuid))
                    .status(stopping ? "COMPLETED" : "RECORDING")
                    .storageType("LOCAL")
                    .objectKey(recordPath)
                    .mediaFormat(mediaFormat)
                    .startedAt(java.time.LocalDateTime.now())
                    .completedAt(stopping ? java.time.LocalDateTime.now() : null)
                    .build();
            callPersistenceService.upsertRecording(entity);
            log.info("💾 [录音] 已登记录音地址: recordingId={}, callId={}, action={}, path={}",
                    recordingId, callUuid, action, recordPath);
        } catch (Exception e) {
            log.warn("⚠️ [录音] 登记录音地址失败: recordingId={}, path={}, err={}",
                    recordingId, recordPath, e.getMessage());
        }
    }

    /**
     * 归一化录音动作取值
     *
     * @param action 原始动作取值
     * @return START 或 STOP
     */
    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_START;
        }
        String normalized = action.trim().toUpperCase();
        return ACTION_STOP.equals(normalized) ? ACTION_STOP : ACTION_START;
    }

    /**
     * 录音路径兜底推导 (仅在路径解析器未装配时使用)
     */
    private static final class ResolvedPath {

        private ResolvedPath() {
        }

        static String recordingIdOf(String callUuid) {
            return com.chandler.fcc.common.recording.RecordingPathLayout.recordingIdOf(callUuid);
        }

        static String defaultPathOf(String callUuid) {
            return com.chandler.fcc.common.recording.RecordingPathLayout
                    .buildAbsolutePath(new com.chandler.fcc.common.recording.RecordingStorageProperties(),
                            callUuid, java.time.LocalDateTime.now());
        }

        static String formatOf(String path) {
            if (path == null) {
                return "wav";
            }
            int dot = path.lastIndexOf('.');
            return (dot < 0 || dot == path.length() - 1) ? "wav" : path.substring(dot + 1).toLowerCase();
        }
    }
}
