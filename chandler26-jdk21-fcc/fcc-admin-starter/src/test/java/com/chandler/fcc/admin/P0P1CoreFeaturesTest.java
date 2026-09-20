package com.chandler.fcc.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallbackTaskEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallSessionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.CallbackTaskMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.*;
import com.chandler.fcc.admin.model.vo.*;
import com.chandler.fcc.admin.service.CallCdrService;
import com.chandler.fcc.admin.service.CallbackTaskService;
import com.chandler.fcc.admin.service.ExtensionService;
import com.chandler.fcc.admin.service.FlowDefinitionService;
import com.chandler.fcc.admin.starter.FccAdminApplication;
import com.chandler.fcc.common.util.IdUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    private FlowDefinitionService flowService;

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
                .tenantId(0L)
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

        PageResult<CallbackTaskVO> page = callbackTaskService.queryCallbacks(CallbackTaskQueryReq.builder()
                .pageNum(1)
                .pageSize(10)
                .customerNumber("13988776655")
                .build());
        assertNotNull(page);
        assertFalse(page.getList().isEmpty());

        // 派单给坐席舒欣
        callbackTaskService.assignTask(taskId, CallbackTaskAssignReq.builder()
                .agentWorkNo("901415")
                .agentName("舒欣")
                .build());

        // 发起一键优先回拨
        callbackTaskService.callTask(taskId);

        // 再次查询校验状态
        PageResult<CallbackTaskVO> afterPage = callbackTaskService.queryCallbacks(CallbackTaskQueryReq.builder()
                .pageNum(1)
                .pageSize(10)
                .status("CALLED")
                .customerNumber("13988776655")
                .build());
        assertNotNull(afterPage);
        assertTrue(afterPage.getList().stream().anyMatch(t -> t.getId().equals(String.valueOf(taskId))));
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
                .tenantId(0L)
                .ctrlId("ctrl-p0-test-" + callId)
                .modelType("INBOUND_CUSTOMER_SERVICE")
                .flowCode("FLOW-INBOUND")
                .routeMode("HTTP_CALLBACK")
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

        CallCdrVO detail = cdrService.getCdrDetail(callId);
        assertNotNull(detail);
        assertEquals("FLOW-INBOUND", detail.getFlowCode());
        assertEquals("HTTP_CALLBACK", detail.getRouteMode());
        assertEquals("01:14", detail.getAudioDuration());
        assertNull(detail.getCallerName());
        assertNull(detail.getCarrier());

        // 只有 Call 摘要事实，不能再要求后端凭空构造四阶段执行轨迹。
        assertEquals(session.getTalkDurationMs(), detail.getTalkDurationMs());
    }

    /**
     * 4. 测试 IVR 流程定义、草稿保存、版本发布与推演仿真
     */
    @Test
    @DisplayName("测试IVR流程编排、草稿保存、版本发布与多分支推演")
    void testFlowDefinitionAndSimulation() {
        // 查询 3 大系统通话流
        List<FlowDefinitionVO> flows = flowService.listFlows();
        assertNotNull(flows);
        assertEquals(3, flows.size());

        // 保存草稿
        String draftVer = flowService.saveDraft("FLOW-INBOUND", FlowSaveDraftReq.builder()
                .version("v1.1.0")
                .routeMode("HTTP_CALLBACK")
                .definitionJson("{\"routeMode\":\"HTTP_CALLBACK\",\"timeout\":800}")
                .build());
        assertEquals("v1.1.0", draftVer);

        // 发布上线
        String pubVer = flowService.publishFlow("FLOW-INBOUND", FlowPublishReq.builder()
                .version("v1.1.0")
                .remark("支持按2业务线司机热线回调800ms熔断")
                .build());
        assertEquals("v1.1.0", pubVer);

        // 多分支仿真推演
        FlowSimulateRespVO sim = flowService.simulateFlow(FlowSimulateReq.builder()
                .flowKey("FLOW-INBOUND")
                .caller("13483983247")
                .did("021-50881001")
                .dtmf("2")
                .routeMode("HTTP_CALLBACK")
                .build());

        assertNotNull(sim);
        assertTrue(sim.getSuccess());
        assertEquals("鹏飞", sim.getTargetAgentName());
        assertEquals("902987", sim.getTargetAgentWorkNo());
        assertFalse(sim.getTraces().isEmpty());
    }
}
