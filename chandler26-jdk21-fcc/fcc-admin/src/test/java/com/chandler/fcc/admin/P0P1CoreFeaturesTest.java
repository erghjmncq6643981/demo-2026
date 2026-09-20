package com.chandler.fcc.admin;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallbackTaskEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallSessionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.CallbackTaskMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.admin.flow.application.FlowStudioService;
import com.chandler.fcc.admin.flow.controller.req.FlowPageReq;
import com.chandler.fcc.admin.flow.controller.req.PublishFlowReq;
import com.chandler.fcc.admin.flow.controller.req.SaveFlowDraftReq;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.*;
import com.chandler.fcc.admin.model.vo.*;
import com.chandler.fcc.admin.service.CallCdrService;
import com.chandler.fcc.admin.service.CallbackTaskService;
import com.chandler.fcc.admin.service.ExtensionService;
import com.chandler.fcc.admin.FccAdminApplication;
import com.chandler.fcc.common.util.IdUtil;
import cn.dev33.satoken.stp.StpUtil;
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
 * P0 & P1 核心功能全链路集成验证测试
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccAdminApplication.class)
@ActiveProfiles("local")
@Transactional
public class P0P1CoreFeaturesTest {

  @Autowired
  private ExtensionService extensionService;

  @Autowired
  private ExtensionMapper extensionMapper;

  @Autowired
  private AgentMapper agentMapper;

  @Autowired
  private CallbackTaskMapper callbackTaskMapper;

  @Autowired
  private CallbackTaskService callbackTaskService;

  @Autowired
  private CallCdrService cdrService;

  @Autowired
  private AdminCallSessionMapper sessionMapper;

  @Autowired
  private FlowStudioService flowService;

  @Autowired
  private FlowDefinitionMapper flowMapper;

  /**
   * 2. 测试未接待回拨待办总池派单与一键回拨
   */
  @Test
  @DisplayName("测试未接待漏话回拨总池查询、派单与回拨闭环")
  void testCallbackTaskWorkflow() {
    LocalDateTime now = LocalDateTime.now();
    Long taskId = IdUtil.nextId();
    CallbackTaskEntity seed = CallbackTaskEntity.builder()
      .id(taskId)
      .sourceCallId(IdUtil.nextId())
      .customerNumber("13988776655")
      .didNumber("021-50881001")
      .missedAt(now.minusMinutes(10))
      .missedReason("坐席忙未接起放弃")
      .waitDurationMs(45000L)
      .status("PENDING")
      .priority(10)
      .callAttempts(0)
      .createdAt(now.minusMinutes(10))
      .updatedAt(now.minusMinutes(10))
      .build();
    callbackTaskMapper.insert(seed);

    PageResult<CallbackTaskVO> page = callbackTaskService.queryCallbacks(
      CallbackTaskQueryReq.builder()
        .pageNum(1)
        .pageSize(10)
        .customerNumber("13988776655")
        .build()
    );
    assertNotNull(page);
    assertFalse(page.getList().isEmpty());

    // 派单给坐席舒欣
    callbackTaskService.assignTask(
      taskId,
      CallbackTaskAssignReq.builder()
        .agentWorkNo("901415")
        .agentName("舒欣")
        .build()
    );

    // 再次查询校验状态
    PageResult<CallbackTaskVO> afterPage = callbackTaskService.queryCallbacks(
      CallbackTaskQueryReq.builder()
        .pageNum(1)
        .pageSize(10)
        .status("ASSIGNED")
        .customerNumber("13988776655")
        .build()
    );
    assertNotNull(afterPage);
    assertTrue(
      afterPage
        .getList()
        .stream()
        .anyMatch(t -> t.getId().equals(String.valueOf(taskId)))
    );
  }

  /**
   * 3. 测试 CDR 持久事实与缺失客户资料边界。
   */
  @Test
  @DisplayName("测试 CDR 持久事实及缺失资料不造假")
  void testCdrTracePipeline() {
    Long callId = IdUtil.nextId();
    LocalDateTime now = LocalDateTime.now();

    CallSessionEntity session = CallSessionEntity.builder()
      .id(callId)
      .ctrlId("ctrl-p0-test-" + callId)
      .modelType("INBOUND_CUSTOMER_SERVICE")
      .flowCode("FLOW-INBOUND")
      .direction("INBOUND")
      .callerNumber("13483983247")
      .destinationNumber("1002")
      .status("COMPLETED")
      .ringDurationMs(5000L)
      .talkDurationMs(74000L)
      .audioDurationSec(74)
      .totalDurationMs(79000L)
      .agentWorkNo("902987")
      .agentName("鹏飞")
      .evaluationScore(5)
      .startedAt(now.minusSeconds(80))
      .answeredAt(now.minusSeconds(75))
      .endedAt(now)
      .createdAt(now)
      .updatedAt(now)
      .build();
    sessionMapper.insert(session);

    try (var authentication = Mockito.mockStatic(StpUtil.class)) {
      CallCdrVO detail = cdrService.getCdrDetail(callId);
      assertNotNull(detail);
      assertEquals("FLOW-INBOUND", detail.getFlowCode());
      assertEquals("01:14", detail.getAudioDuration());
      assertNull(detail.getCallerName());
      assertNull(detail.getCarrier());

      // 只有 Call 摘要事实，不能再要求后端凭空构造四阶段执行轨迹。
      assertEquals(session.getTalkDurationMs(), detail.getTalkDurationMs());
    }
  }

  /** 验证显式创建的固定阶段 IVR 定义可以保存并发布。 */
  @Test
  @DisplayName("测试固定阶段 IVR 草稿发布边界")
  void testStagedFlowDraftPublication() {
    var fixture =
      FlowDefinitionEntity.builder()
        .id(IdUtil.nextId())
        .flowKey("TEST-IVR-FLOW")
        .flowName("测试 IVR 流程")
        .modelType("INBOUND_CUSTOMER_SERVICE")
        .status("DRAFT")
        .currentVersion(0)
        .build();
    flowMapper.insert(fixture);
    // 此用例验证版本持久化，权限边界由独立测试覆盖。
    try (
      var auth = Mockito.mockStatic(StpUtil.class);
    ) {
      var draftRequest = new SaveFlowDraftReq();
      draftRequest.setDefinitionJson(
        """
        {"routeMode":"IVR","template":"INBOUND",
         "menu":{"enabled":false,"prompt":"","timeoutSeconds":10},"branches":[],
         "defaultRoute":{"targetType":"AGENT","target":"901001","queueSeconds":60},
         "timeoutAction":"CALLBACK"}
        """
      );
      String version = flowService.saveDraft("TEST-IVR-FLOW", draftRequest).getVersion();
      assertEquals("v1.0.0", version);
      var publishRequest = new PublishFlowReq();
      publishRequest.setVersion(version);
      publishRequest.setRemark("测试发布");
      assertEquals(
        version,
        flowService.publish("TEST-IVR-FLOW", publishRequest).getVersion()
      );
      assertEquals(
        "PUBLISHED",
        flowService.versions("TEST-IVR-FLOW", new FlowPageReq()).getList().getFirst().getPublishStatus()
      );
      var invalidRequest = new SaveFlowDraftReq();
      invalidRequest.setDefinitionJson("{\"routeMode\":\"HTTP_CALLBACK\"}");
      assertThrows(IllegalArgumentException.class, () ->
        flowService.saveDraft("TEST-IVR-FLOW", invalidRequest)
      );
      auth.verify(
        () -> StpUtil.checkPermission("flow:write"),
        Mockito.times(3)
      );
    }
  }
}
