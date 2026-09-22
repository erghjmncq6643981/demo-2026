package com.chandler.fcc.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallLegMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallRecordingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallSessionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.CallCdrQueryReq;
import com.chandler.fcc.admin.model.vo.CallCdrStatsVO;
import com.chandler.fcc.admin.model.vo.CallCdrVO;
import com.chandler.fcc.admin.model.vo.CallLegVO;
import com.chandler.fcc.common.recording.RecordingPathLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 历史通话话单 (CDR) 与录音检索业务服务
 * <p>
 * 提供通话全生命周期流水检索、Leg 通话信道关联、录音回溯与坐席服务统计。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallCdrService {

  private final AdminCallSessionMapper sessionMapper;
  private final AdminCallLegMapper legMapper;
  private final AdminCallRecordingMapper recordingMapper;
  private final AgentMapper agentMapper;

  /**
   * 多条件分页查询通话话单 CDR 列表
   *
   * @param req 检索过滤参数
   * @return 话单视图对象分页容器
   */
  public PageResult<CallCdrVO> queryCdrs(CallCdrQueryReq req) {
    StpUtil.checkPermission("cdr:view");
    Page<CallSessionEntity> page = new Page<>(
      req.getPageNum(),
      req.getPageSize()
    );
    LambdaQueryWrapper<CallSessionEntity> wrapper = buildFilterWrapper(req)
      .orderByDesc(CallSessionEntity::getStartedAt);

    Page<CallSessionEntity> entityPage = sessionMapper.selectPage(
      page,
      wrapper
    );

    List<CallCdrVO> voList = entityPage
      .getRecords()
      .stream()
      .map(this::buildCdrVO)
      .toList();

    return PageResult.<CallCdrVO>builder()
      .pageNum(entityPage.getCurrent())
      .pageSize(entityPage.getSize())
      .total(entityPage.getTotal())
      .list(voList)
      .build();
  }

  /**
   * 按统计区间聚合话单 KPI 指标 (今日呼叫总数 / 接通数 / 通话时长)
   * <p>
   * 独立于分页列表：由数据库直接聚合全量事实，因此不会随列表翻页而变化。
   * </p>
   *
   * @return 话单指标聚合视图
   */
  public CallCdrStatsVO queryTodayStats() {
    StpUtil.checkPermission("cdr:view");
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    Map<String, Object> row = sessionMapper.selectStatsSince(todayStart);

    long totalCalls = toLong(row == null ? null : row.get("totalCalls"));
    long answeredCalls = toLong(row == null ? null : row.get("answeredCalls"));
    long totalTalkSec =
      toLong(row == null ? null : row.get("totalTalkMs")) / 1000L;
    long inboundTalkSec =
      toLong(row == null ? null : row.get("inboundTalkMs")) / 1000L;
    long outboundTalkSec =
      toLong(row == null ? null : row.get("outboundTalkMs")) / 1000L;

    return CallCdrStatsVO.builder()
      .totalCalls(totalCalls)
      .answeredCalls(answeredCalls)
      .totalTalkSec(totalTalkSec)
      .inboundTalkSec(inboundTalkSec)
      .outboundTalkSec(outboundTalkSec)
      .build();
  }

  /**
   * 构造话单检索条件
   * <p>
   * 全部条件下推到 MySQL 执行，保证筛选结果基于完整数据集，
   * 而不是「先在浏览器里取一页再本地过滤」。
   * </p>
   *
   * @param req 检索请求
   * @return MyBatis-Plus 条件构造器
   */
  private LambdaQueryWrapper<CallSessionEntity> buildFilterWrapper(CallCdrQueryReq req) {
    LambdaQueryWrapper<CallSessionEntity> wrapper = new LambdaQueryWrapper<>();

    // 号码关键字：主叫或被叫任一命中
    String number = trimToNull(req.getNumber());
    wrapper.and(number != null, w ->
      w
        .like(CallSessionEntity::getCallerNumber, number)
        .or()
        .like(CallSessionEntity::getDestinationNumber, number)
    );

    // 主叫号码独立检索
    String caller = trimToNull(req.getCaller());
    wrapper.like(caller != null, CallSessionEntity::getCallerNumber, caller);

    // 被叫号码独立检索
    String callee = trimToNull(req.getCallee());
    wrapper.like(
      callee != null,
      CallSessionEntity::getDestinationNumber,
      callee
    );

    // 坐席工号只匹配新模型的事实列 agent_work_no。
    String agentWorkNo = trimToNull(req.getAgentWorkNo());
    wrapper.like(
      agentWorkNo != null,
      CallSessionEntity::getAgentWorkNo,
      agentWorkNo
    );

    // 坐席姓名：优先匹配话单冗余列；在册坐席姓名变更时回退到 fcc_agent 反查工号集合
    String agentName = trimToNull(req.getAgentName());
    if (agentName != null) {
      List<String> matchedWorkNos = agentMapper
        .selectList(
          new LambdaQueryWrapper<AgentEntity>().like(AgentEntity::getAgentName, agentName)
        )
        .stream()
        .map(AgentEntity::getWorkNo)
        .filter(workNo -> workNo != null && !workNo.isBlank())
        .toList();
      wrapper.and(w -> {
        w.like(CallSessionEntity::getAgentName, agentName);
        if (!matchedWorkNos.isEmpty()) {
          w.or().in(CallSessionEntity::getAgentWorkNo, matchedWorkNos);
        }
      });
    }

    // 通话ID / 控制标识
    String ctrlId = trimToNull(req.getCtrlId());
    wrapper.like(ctrlId != null, CallSessionEntity::getCtrlId, ctrlId);

    String direction = trimToNull(req.getDirection());
    wrapper.eq(direction != null, CallSessionEntity::getDirection, direction);

    String status = trimToNull(req.getStatus());
    wrapper.eq(status != null, CallSessionEntity::getStatus, status);

    String hangupCause = trimToNull(req.getHangupCause());
    wrapper.eq(
      hangupCause != null,
      CallSessionEntity::getHangupCause,
      hangupCause
    );

    wrapper.ge(
      req.getStartTime() != null,
      CallSessionEntity::getStartedAt,
      req.getStartTime()
    );
    wrapper.le(
      req.getEndTime() != null,
      CallSessionEntity::getStartedAt,
      req.getEndTime()
    );

    return wrapper;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * 将数据库聚合返回的数值安全归一化为 long (兼容 Long / BigDecimal / Integer)
   */
  private static long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return 0L;
  }

  /**
   * 查询单通通话详情与全部信道 Leg 明细
   *
   * @param id 会话主键 ID
   * @return 话单聚合视图
   */
  public CallCdrVO getCdrDetail(Long id) {
    StpUtil.checkPermission("cdr:view");
    CallSessionEntity session = sessionMapper.selectOne(
      new LambdaQueryWrapper<CallSessionEntity>()
        .eq(CallSessionEntity::getId, id)
    );
    if (session == null) {
      return null;
    }
    CallCdrVO vo = buildCdrVO(session);

    // 填充信道 Leg 明细
    List<CallLegEntity> legs = legMapper.selectList(
      new LambdaQueryWrapper<CallLegEntity>()
        .eq(CallLegEntity::getCallId, id)
        .orderByAsc(CallLegEntity::getId)
    );

    List<CallLegVO> legVOs = legs
      .stream()
      .map(l ->
        CallLegVO.builder()
          .id(l.getId())
          .legUuid(l.getChannelUuid())
          .legType(l.getRoleType())
          .fromNumber(l.getCallerNumber())
          .toNumber(l.getDestinationNumber())
          .endpointType(l.getEndpointType())
          .routingContext(l.getRoutingContext())
          .status(l.getState())
          .ringDurationMs(l.getRingDurationMs())
          .billDurationMs(l.getTalkDurationMs())
          .build()
      )
      .toList();

    vo.setLegs(legVOs);
    return vo;
  }

  /**
   * 转换为话单 VO 并关联坐席姓名、归属地、运营商与录音访问路径
   */
  private CallCdrVO buildCdrVO(CallSessionEntity session) {
    // 1. 查询坐席姓名 (优先从实体直读)
    String agentWorkNo = session.getAgentWorkNo();
    String agentName = session.getAgentName();
    if (agentName == null && agentWorkNo != null) {
      AgentEntity agent = agentMapper.selectOne(
        new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getWorkNo, agentWorkNo)
      );
      if (agent != null) {
        agentName = agent.getAgentName();
      }
    }

    // 2. 关联录音：以 fcc_call_recording.object_key 记录的共享存储地址为事实来源
    //    本地共享存储走管理端流式代理 (复播支持 Range)，远端对象存储地址则直接交给浏览器
    String recordingUrl = null;
    String recordingDownloadUrl = null;
    String recordingPath = null;
    String recordingId = null;
    Long recordingDurationMs = null;
    List<CallRecordingEntity> recordings = recordingMapper.selectList(
      new LambdaQueryWrapper<CallRecordingEntity>()
        .eq(CallRecordingEntity::getCallId, session.getId())
        .orderByDesc(CallRecordingEntity::getStartedAt)
        .orderByDesc(CallRecordingEntity::getId)
    );
    if (!recordings.isEmpty()) {
      CallRecordingEntity latest = recordings.getFirst();
      recordingPath = latest.getObjectKey();
      recordingId = latest.getRecordingId();
      recordingDurationMs = latest.getDurationMs();
      if (RecordingPathLayout.isRemoteUrl(recordingPath)) {
        recordingUrl = recordingPath;
        recordingDownloadUrl = recordingPath;
      } else if (recordingId != null && !recordingId.isBlank()) {
        recordingUrl =
          "/api/admin/recordings/by-rec-id/" + recordingId + "/stream";
        recordingDownloadUrl =
          "/api/admin/recordings/by-rec-id/" + recordingId + "/download";
      } else {
        recordingUrl = "/api/admin/recordings/" + session.getId() + "/stream";
        recordingDownloadUrl =
          "/api/admin/recordings/" + session.getId() + "/download";
      }
    }

    // 3. 计算展示录音时长 (分:秒)
    //    优先取录音事实的真实时长；未登记录音事实时回落到通话时长。
    //    注意：本字段只表达"时长"，是否能复播由 recordingUrl 是否为空表达，
    //    两者不可混用，否则会出现有按钮但点开 404 的假入口。
    long talkSec = session.getAudioDurationSec() != null &&
      session.getAudioDurationSec() > 0
      ? session.getAudioDurationSec()
      : (session.getTalkDurationMs() != null
          ? session.getTalkDurationMs() / 1000
          : 0);
    long recordSec = recordingDurationMs != null && recordingDurationMs > 0
      ? recordingDurationMs / 1000
      : talkSec;
    String audioDuration = String.format(
      "%02d:%02d",
      recordSec / 60,
      recordSec % 60
    );

    // 4. 对外话单仅暴露已接听与未接听结果，不暴露内部流转状态。
    boolean answered =
      session.getAnsweredAt() != null ||
      (session.getTalkDurationMs() != null &&
        session.getTalkDurationMs() > 0) ||
      (session.getAudioDurationSec() != null &&
        session.getAudioDurationSec() > 0) ||
      "ANSWERED".equalsIgnoreCase(session.getStatus()) ||
      "COMPLETED".equalsIgnoreCase(session.getStatus()) ||
      "ANSWER".equalsIgnoreCase(session.getResult());

    String normalizedStatus = answered ? "ANSWERED" : "NO_ANSWER";
    String answerType = answered ? "ANSWER" : "MISSED";

    return CallCdrVO.builder()
      .id(session.getId())
      .ctrlId(session.getCtrlId())
      .bizId(session.getBizId())
      .modelType(session.getModelType())
      .flowCode(session.getFlowCode())
      .routeTargetType(session.getRouteTargetType())
      .routeTargetId(session.getRouteTargetId())
      .direction(session.getDirection())
      .caller(session.getCallerNumber())
      .callerName(null)
      .carrier(null)
      .callee(session.getDestinationNumber())
      .status(normalizedStatus)
      .answerType(answerType)
      .hangupCause(session.getHangupCause())
      .agentWorkNo(agentWorkNo)
      .agentName(agentName)
      .waitDurationMs(session.getRingDurationMs())
      .talkDurationMs(session.getTalkDurationMs())
      .audioDuration(audioDuration)
      .totalDurationMs(session.getTotalDurationMs())
      .evaluationScore(session.getEvaluationScore())
      .recordingUrl(recordingUrl)
      .initiatedAt(session.getStartedAt())
      .answeredAt(session.getAnsweredAt())
      .endedAt(session.getEndedAt())
      .legs(Collections.emptyList())
      .build();
  }

}
