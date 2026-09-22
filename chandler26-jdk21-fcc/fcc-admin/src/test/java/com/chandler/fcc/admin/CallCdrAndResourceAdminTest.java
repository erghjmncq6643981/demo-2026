package com.chandler.fcc.admin;

import cn.dev33.satoken.stp.StpUtil;
import static org.junit.jupiter.api.Assertions.*;

import com.chandler.fcc.admin.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallRecordingEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallLegMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallRecordingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallSessionMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.*;
import com.chandler.fcc.admin.model.vo.*;
import com.chandler.fcc.admin.service.CallCdrService;
import com.chandler.fcc.admin.service.ClientFleetService;
import com.chandler.fcc.admin.service.SystemConfigService;
import com.chandler.fcc.admin.service.TelephonyResourceService;
import com.chandler.fcc.admin.FccAdminApplication;
import com.chandler.fcc.common.util.IdUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.Mockito;

/**
 * 通信资源管理、动态系统配置、机队治理与历史话单 CDR 综合集成测试
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccAdminApplication.class)
@ActiveProfiles("local")
@Transactional
public class CallCdrAndResourceAdminTest {

  @Autowired
  private TelephonyResourceService resourceService;

  @Autowired
  private SystemConfigService configService;

  @Autowired
  private ClientFleetService fleetService;

  @Autowired
  private CallCdrService cdrService;

  @Autowired
  private AdminCallSessionMapper sessionMapper;

  @Autowired
  private AdminCallLegMapper legMapper;

  @Autowired
  private AdminCallRecordingMapper recordingMapper;

  /**
   * 测试拨号上下文、DID引示号与外呼号池维护
   */
  @Test
  @DisplayName("测试拨号上下文、DID呼入引示号与外呼号池录入与查询")
  void testResourceManagement() {
    String routingContext = "telecom";

    // 1. DID 号码
    Long didId = resourceService.createDidNumber(
      DidNumberCreateReq.builder()
        .phoneNumber("01099998888")
        .routingContext(routingContext)
        .routeKey("IVR_HOTLINE")
        .build()
    );
    assertNotNull(didId);

    List<DidNumberVO> dids = resourceService.listDidNumbers();
    assertTrue(
      dids.stream().anyMatch(d -> d.getPhoneNumber().equals("01099998888"))
    );

    // 2. 外呼号码池
    Long outId = resourceService.createOutboundNumber(
      OutboundNumberCreateReq.builder()
        .phoneNumber("02195588001")
        .routingContext(routingContext)
        .poolCode("vip")
        .maxConcurrent(3)
        .build()
    );
    assertNotNull(outId);

    List<OutboundNumberVO> outbounds = resourceService.listOutboundNumbers(
      "vip"
    );
    assertTrue(
      outbounds.stream().anyMatch(o -> o.getPhoneNumber().equals("02195588001"))
    );
  }

  /**
   * 测试系统参数多作用域动态配置与机队版本管理
   */
  @Test
  @DisplayName("测试多作用域系统配置与PC客户端版本机队治理")
  void testSystemConfigAndFleet() {
    // 1. 动态配置
    Long cfgId = configService.saveOrUpdateConfig(
      SystemConfigReq.builder()
        .propName("call.recording.auto-upload")
        .propValue("true")
        .propType("BOOLEAN")
        .scope("BACKEND")
        .description("通话挂机自动上传OSS开关")
        .build(),
      "admin"
    );
    assertNotNull(cfgId);

    List<SystemConfigVO> configs = configService.listConfigsByScope("BACKEND");
    assertTrue(
      configs
        .stream()
        .anyMatch(c -> "call.recording.auto-upload".equals(c.getPropName()))
    );

    // 2. 客户端版本发布
    Long verId = fleetService.publishVersion(
      ClientVersionReleaseReq.builder()
        .version("1.3.0")
        .platform("WINDOWS")
        .downloadUrl("https://oss.example.com/client/v1.3.0.exe")
        .fileMd5("d41d8cd98f00b204e9800998ecf8427e")
        .forceUpdate(false)
        .releaseNotes("优化WebRTC媒体通道稳定性")
        .build(),
      "admin"
    );
    assertNotNull(verId);

    List<ClientVersionReleaseVO> versions = fleetService.listVersions(
      "WINDOWS"
    );
    assertTrue(versions.stream().anyMatch(v -> "1.3.0".equals(v.getVersion())));

    // 3. 坐席硬件指纹安全审计记录
    Long hwId = fleetService.recordHardware(
      10001L,
      "901001",
      "1.3.0",
      "00:1A:2B:3C:4D:5E",
      "Windows 11 Enterprise",
      "192.168.1.188"
    );
    assertNotNull(hwId);

    List<ClientHardwareRecordVO> audits = fleetService.listHardwareAudit(
      10001L
    );
    assertFalse(audits.isEmpty());
    assertEquals("901001", audits.getFirst().getWorkNum());
  }

  /**
   * 测试通话话单 CDR 多条件分页检索与 Leg/录音全流程溯源
   */
  @Test
  @DisplayName("测试历史话单CDR分页检索、分段信道Leg追踪与录音质检评价联动")
  void testCdrPaginationAndDetail() {
    Long sessionId = IdUtil.nextId();
    LocalDateTime now = LocalDateTime.now();

    // 1. 插入测试通话会话
    CallSessionEntity session = CallSessionEntity.builder()
      .id(sessionId)
      .bizId("TEST-BIZ-888")
      .ctrlId("ctrl-test-888")
      .modelType("INBOUND_CUSTOMER_SERVICE")
      .direction("INBOUND")
      .callerNumber("13800138999")
      .destinationNumber("1001")
      .status("COMPLETED")
      .hangupCause("NORMAL_CLEARING")
      .primaryWorkNo("901001")
      .agentWorkNo("901001")
      .ringDurationMs(3200L)
      .talkDurationMs(48000L)
      .totalDurationMs(51200L)
      .evaluationScore(5)
      .attributes(
        "{\"recording_path\":\"http://storage.example.com/recordings/test.wav\"}"
      )
      .startedAt(now.minusSeconds(60))
      .answeredAt(now.minusSeconds(50))
      .endedAt(now)
      .createdAt(now)
      .updatedAt(now)
      .build();
    sessionMapper.insert(session);

    // 2. 插入信道 Leg
    CallLegEntity callerLeg = CallLegEntity.builder()
      .id(IdUtil.nextId())
      .callId(sessionId)
      .channelUuid("test-leg-caller-uuid-888")
      .nodeId("node-01")
      .roleType("CALLER")
      .direction("INBOUND")
      .callerNumber("13800138999")
      .destinationNumber("01088889999")
      .endpointType("SIP")
      .routingContext("telecom")
      .state("DESTROY")
      .createdTime(now.minusSeconds(60))
      .ringDurationMs(3200L)
      .talkDurationMs(48000L)
      .hangupCause("NORMAL_CLEARING")
      .createdAt(now)
      .updatedAt(now)
      .build();
    legMapper.insert(callerLeg);

    // 3. 插入录音记录
    CallRecordingEntity recording = CallRecordingEntity.builder()
      .id(IdUtil.nextId())
      .callId(sessionId)
      .recordingId("rec-test-888")
      .status("COMPLETED")
      .storageType("OBJECT_STORAGE")
      .objectKey("http://storage.example.com/recordings/test.wav")
      .mediaFormat("WAV")
      .durationMs(48000L)
      .startedAt(now.minusSeconds(50))
      .completedAt(now)
      .createdAt(now)
      .updatedAt(now)
      .build();
    recordingMapper.insert(recording);

    try (var authentication = Mockito.mockStatic(StpUtil.class)) {
      // 4. 多条件分页查询验证
      PageResult<CallCdrVO> cdrPage = cdrService.queryCdrs(
        CallCdrQueryReq.builder()
          .caller("13800138999")
          .agentWorkNo("901001")
          .direction("INBOUND")
          .status("COMPLETED")
          .pageNum(1)
          .pageSize(10)
          .build()
      );

      assertNotNull(cdrPage);
      assertTrue(cdrPage.getTotal() >= 1);
      CallCdrVO found = cdrPage
        .getList()
        .stream()
        .filter(c -> c.getId().equals(sessionId))
        .findFirst()
        .orElse(null);
      assertNotNull(found);
      assertEquals("13800138999", found.getCaller());
      assertEquals(5, found.getEvaluationScore());
      assertEquals(
        "http://storage.example.com/recordings/test.wav",
        found.getRecordingUrl()
      );

      // 5. 详情查询验证 Leg 明细
      CallCdrVO detail = cdrService.getCdrDetail(sessionId);
      assertNotNull(detail);
      assertFalse(detail.getLegs().isEmpty());
      assertEquals("CALLER", detail.getLegs().getFirst().getLegType());
      assertEquals("telecom", detail.getLegs().getFirst().getRoutingContext());
      assertNull(detail.getLegs().getFirst().getReadCodec());
    }
  }
}
