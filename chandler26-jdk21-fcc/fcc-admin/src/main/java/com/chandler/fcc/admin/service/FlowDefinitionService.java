package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.admin.model.dto.FlowPublishReq;
import com.chandler.fcc.admin.model.dto.FlowSaveDraftReq;
import com.chandler.fcc.admin.model.dto.FlowSimulateReq;
import com.chandler.fcc.admin.model.vo.CallTraceStepVO;
import com.chandler.fcc.admin.model.vo.FlowDefinitionVO;
import com.chandler.fcc.admin.model.vo.FlowSimulateRespVO;
import com.chandler.fcc.admin.model.vo.FlowVersionVO;
import com.chandler.fcc.common.util.IdUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 业务通话流程定义与版本编排管理服务
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDefinitionService {

    private final FlowDefinitionMapper flowMapper;
    private final FlowDefinitionVersionMapper versionMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /**
     * 自动初始化 3 大核心系统通话流程主数据与 v1.0.0 线上版本
     */
    @PostConstruct
    public void initSystemFlowsIfEmpty() {
        try {
            Long count = flowMapper.selectCount(null);
            if (count == null || count == 0) {
                LocalDateTime now = LocalDateTime.now();

                // 1. 来电流程
                Long inboundId = IdUtil.nextId();
                FlowDefinitionEntity inbound = FlowDefinitionEntity.builder()
                        .id(inboundId)
                        .tenantId(0L)
                        .flowKey("FLOW-INBOUND")
                        .flowName("来电流程")
                        .modelType("INBOUND")
                        .status("PUBLISHED")
                        .currentVersion(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                flowMapper.insert(inbound);
                insertInitialVersions(inboundId, "FLOW-INBOUND", now);

                // 2. 外呼
                Long outboundId = IdUtil.nextId();
                FlowDefinitionEntity outbound = FlowDefinitionEntity.builder()
                        .id(outboundId)
                        .tenantId(0L)
                        .flowKey("FLOW-OUTBOUND")
                        .flowName("外呼")
                        .modelType("OUTBOUND")
                        .status("PUBLISHED")
                        .currentVersion(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                flowMapper.insert(outbound);
                insertInitialVersions(outboundId, "FLOW-OUTBOUND", now);

                // 3. 话机直接外呼
                Long phoneDirectId = IdUtil.nextId();
                FlowDefinitionEntity phoneDirect = FlowDefinitionEntity.builder()
                        .id(phoneDirectId)
                        .tenantId(0L)
                        .flowKey("FLOW-PHONE-DIRECT")
                        .flowName("话机直接外呼")
                        .modelType("PHONE_DIRECT")
                        .status("PUBLISHED")
                        .currentVersion(1)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                flowMapper.insert(phoneDirect);
                insertInitialVersions(phoneDirectId, "FLOW-PHONE-DIRECT", now);

                log.info("[FlowDefinitionService] 成功初始化系统 3 大通话流程及历史版本快照");
            }
        } catch (Exception e) {
            log.warn("[FlowDefinitionService] 初始化系统流程略过: {}", e.getMessage());
        }
    }

    private void insertInitialVersions(Long flowId, String flowKey, LocalDateTime now) {
        // v0.9.0 归档版
        versionMapper.insert(FlowDefinitionVersionEntity.builder()
                .id(IdUtil.nextId())
                .flowDefinitionId(flowId)
                .versionNo(0)
                .definitionJson("{\"version\":\"v0.9.0\",\"status\":\"ARCHIVED\"}")
                .checksum("sha256-archive-v090")
                .publishStatus("ARCHIVED")
                .publishedAt(now.minusDays(30))
                .createdBy("admin")
                .createdAt(now.minusDays(30))
                .build());

        // v1.0.0 线上正式版
        versionMapper.insert(FlowDefinitionVersionEntity.builder()
                .id(IdUtil.nextId())
                .flowDefinitionId(flowId)
                .versionNo(1)
                .definitionJson("{\"version\":\"v1.0.0\",\"routeMode\":\"HTTP_CALLBACK\",\"status\":\"PUBLISHED\"}")
                .checksum("sha256-prod-v100")
                .publishStatus("PUBLISHED")
                .publishedAt(now.minusDays(7))
                .createdBy("admin")
                .createdAt(now.minusDays(7))
                .build());

        // v1.1.0 草稿编辑中
        versionMapper.insert(FlowDefinitionVersionEntity.builder()
                .id(IdUtil.nextId())
                .flowDefinitionId(flowId)
                .versionNo(2)
                .definitionJson("{\"version\":\"v1.1.0\",\"routeMode\":\"HTTP_CALLBACK\",\"status\":\"DRAFT\"}")
                .checksum("sha256-draft-v110")
                .publishStatus("DRAFT")
                .publishedAt(null)
                .createdBy("admin")
                .createdAt(now)
                .build());
    }

    /**
     * 查询所有流程列表及包含的版本信息
     */
    public List<FlowDefinitionVO> listFlows() {
        List<FlowDefinitionEntity> entities = flowMapper.selectList(
                new LambdaQueryWrapper<FlowDefinitionEntity>()
                        .isNull(FlowDefinitionEntity::getDeletedAt)
                        .orderByAsc(FlowDefinitionEntity::getId));

        List<FlowDefinitionVO> vos = new ArrayList<>();
        for (FlowDefinitionEntity e : entities) {
            List<FlowVersionVO> versions = getVersions(e.getFlowKey());
            String curVerStr = "v1." + (e.getCurrentVersion() != null ? e.getCurrentVersion() - 1 : 0) + ".0";
            if (e.getCurrentVersion() == 1) {
                curVerStr = "v1.0.0";
            }
            vos.add(FlowDefinitionVO.builder()
                    .id(e.getId())
                    .flowKey(e.getFlowKey())
                    .flowName(e.getFlowName())
                    .modelType(e.getModelType())
                    .status(e.getStatus())
                    .currentVersion(curVerStr)
                    .versions(versions)
                    .build());
        }
        return vos;
    }

    /**
     * 获取指定流程的所有历史和草稿版本
     */
    public List<FlowVersionVO> getVersions(String flowKey) {
        FlowDefinitionEntity flow = flowMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionEntity>()
                        .eq(FlowDefinitionEntity::getFlowKey, flowKey));
        if (flow == null) {
            return List.of();
        }

        List<FlowDefinitionVersionEntity> versionEntities = versionMapper.selectList(
                new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                        .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                        .orderByDesc(FlowDefinitionVersionEntity::getVersionNo));

        return versionEntities.stream().map(v -> {
            String verStr = switch (v.getVersionNo()) {
                case 0 -> "v0.9.0";
                case 1 -> "v1.0.0";
                case 2 -> "v1.1.0";
                default -> "v1." + (v.getVersionNo() - 1) + ".0";
            };
            return FlowVersionVO.builder()
                    .version(verStr)
                    .versionNo(v.getVersionNo())
                    .publishStatus(v.getPublishStatus())
                    .definitionJson(v.getDefinitionJson())
                    .publishedAt(v.getPublishedAt())
                    .createdBy(v.getCreatedBy())
                    .createdAt(v.getCreatedAt())
                    .build();
        }).toList();
    }

    /**
     * 保存流程草稿配置
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveDraft(String flowKey, FlowSaveDraftReq req) {
        FlowDefinitionEntity flow = flowMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionEntity>()
                        .eq(FlowDefinitionEntity::getFlowKey, flowKey));
        if (flow == null) {
            throw new IllegalArgumentException("流程定义不存在: " + flowKey);
        }

        LocalDateTime now = LocalDateTime.now();
        String json = req.getDefinitionJson() != null ? req.getDefinitionJson() : "{}";
        String checksum = calculateSha256(json);

        // 查找现有的草稿版本 (DRAFT)
        FlowDefinitionVersionEntity draft = versionMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                        .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                        .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT"));

        if (draft != null) {
            draft.setDefinitionJson(json);
            draft.setChecksum(checksum);
            versionMapper.updateById(draft);
            log.info("[FlowDefinitionService] 更新流程草稿: flowKey={}, versionNo={}", flowKey, draft.getVersionNo());
        } else {
            draft = FlowDefinitionVersionEntity.builder()
                    .id(IdUtil.nextId())
                    .flowDefinitionId(flow.getId())
                    .versionNo(2) // 默认草稿对应 2
                    .definitionJson(json)
                    .checksum(checksum)
                    .publishStatus("DRAFT")
                    .createdBy("admin")
                    .createdAt(now)
                    .build();
            versionMapper.insert(draft);
            log.info("[FlowDefinitionService] 创建新流程草稿: flowKey={}", flowKey);
        }

        flow.setUpdatedAt(now);
        flowMapper.updateById(flow);

        return req.getVersion() != null ? req.getVersion() : "v1.1.0";
    }

    /**
     * 正式发布版本上线
     */
    @Transactional(rollbackFor = Exception.class)
    public String publishFlow(String flowKey, FlowPublishReq req) {
        FlowDefinitionEntity flow = flowMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionEntity>()
                        .eq(FlowDefinitionEntity::getFlowKey, flowKey));
        if (flow == null) {
            throw new IllegalArgumentException("流程定义不存在: " + flowKey);
        }

        LocalDateTime now = LocalDateTime.now();

        // 查找待发布的草稿
        FlowDefinitionVersionEntity draft = versionMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                        .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                        .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT"));

        if (draft == null) {
            // 如果没有草稿，以 v1.0.0 为准
            return "v1.0.0";
        }

        // 将之前 PUBLISHED 的版本归档 ARCHIVED
        List<FlowDefinitionVersionEntity> oldPubs = versionMapper.selectList(
                new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                        .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                        .eq(FlowDefinitionVersionEntity::getPublishStatus, "PUBLISHED"));
        for (FlowDefinitionVersionEntity oldPub : oldPubs) {
            oldPub.setPublishStatus("ARCHIVED");
            versionMapper.updateById(oldPub);
        }

        // 将草稿版本转为 PUBLISHED
        draft.setPublishStatus("PUBLISHED");
        draft.setPublishedAt(now);
        versionMapper.updateById(draft);

        // 更新流程主表当前版本号与状态
        flow.setCurrentVersion(draft.getVersionNo());
        flow.setStatus("PUBLISHED");
        flow.setUpdatedAt(now);
        flowMapper.updateById(flow);

        // 动态通知呼叫引擎 (fcc-server) 热加载生效
        notifyEngineReload(flowKey);

        log.info("[FlowDefinitionService] 流程热发布成功并已触发呼叫引擎热加载: flowKey={}, version={}", flowKey, req.getVersion());
        return req.getVersion();
    }

    private void notifyEngineReload(String flowKey) {
        // 1. Redis 广播通知
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.convertAndSend("fcc:flow:publish", flowKey);
                log.info("📢 [FlowDefinitionService] 已广播流程发布事件至 Redis: fcc:flow:publish, flowKey={}", flowKey);
            } catch (Exception re) {
                log.warn("⚠️ [FlowDefinitionService] Redis 广播发布事件略过: {}", re.getMessage());
            }
        }
        // 2. HTTP 异步通知 fcc-server (8085)
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://127.0.0.1:8085/api/telephony/call/flow/reload?flowKey=" + flowKey))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .timeout(java.time.Duration.ofMillis(1500))
                    .build();
            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            log.info("📡 [FlowDefinitionService] 已发送 HTTP 热重载指令至 fcc-server");
        } catch (Exception httpEx) {
            log.warn("⚠️ [FlowDefinitionService] HTTP 热重载通知略过: {}", httpEx.getMessage());
        }
    }

    /**
     * 流程多分支仿真模拟运行推演
     */
    public FlowSimulateRespVO simulateFlow(FlowSimulateReq req) {
        String simId = "SIM-" + System.currentTimeMillis();
        String routeMode = req.getRouteMode() != null ? req.getRouteMode() : "HTTP_CALLBACK";
        String dtmf = req.getDtmf() != null ? req.getDtmf() : "2";

        List<CallTraceStepVO> traces = new ArrayList<>();

        // Stage 1: TRIGGER
        traces.add(CallTraceStepVO.builder()
                .timeOffset("+00:00.0s")
                .stage("TRIGGER")
                .stageName("起呼触发")
                .actionCode("ANSWER")
                .actionName("通道应答与识别")
                .detail("模拟呼入到达，识别主叫号码 " + (req.getCaller() != null ? req.getCaller() : "13483983247"))
                .status("SUCCESS")
                .duration("12ms")
                .build());

        // Stage 2: ROUTE
        traces.add(CallTraceStepVO.builder()
                .timeOffset("+00:00.3s")
                .stage("ROUTE")
                .stageName("路由决策")
                .actionCode("READ_DTMF")
                .actionName("按键收号导航")
                .detail("模拟用户按键输入: [" + dtmf + "]")
                .status("SUCCESS")
                .duration("1.2s")
                .build());

        String decisionResult;
        String targetWorkNo = "902987";
        String targetName = "鹏飞";

        if ("DID_DIRECT".equalsIgnoreCase(routeMode)) {
            targetWorkNo = "901001";
            targetName = "钱丁君";
            decisionResult = "DID 直达专席 ➔ 坐席: 钱丁君 (901001)";
            traces.add(CallTraceStepVO.builder()
                    .timeOffset("+00:01.5s")
                    .stage("ROUTE")
                    .stageName("路由决策")
                    .actionCode("DID_DIRECT")
                    .actionName("DID 专线号码直通")
                    .detail("专线 " + (req.getDid() != null ? req.getDid() : "021-50881001") + " 命中专席: 钱丁君 (901001)")
                    .status("SUCCESS")
                    .duration("8ms")
                    .build());
        } else if ("RULE_ENGINE".equalsIgnoreCase(routeMode)) {
            targetWorkNo = "901415";
            targetName = "舒欣";
            decisionResult = "多维规则引擎 ➔ 技能组排队分配坐席: 舒欣 (901415)";
            traces.add(CallTraceStepVO.builder()
                    .timeOffset("+00:01.5s")
                    .stage("ROUTE")
                    .stageName("路由决策")
                    .actionCode("RULE_ENGINE")
                    .actionName("多维规则引擎决策")
                    .detail("时间规则(09:00-18:00) 命中工作时间窗，VIP客户权重加成，分配至白班一组坐席 舒欣")
                    .status("SUCCESS")
                    .duration("32ms")
                    .build());
        } else {
            decisionResult = "HTTP 接口回调 ➔ 业务线匹配坐席: 鹏飞 (902987)";
            traces.add(CallTraceStepVO.builder()
                    .timeOffset("+00:01.5s")
                    .stage("ROUTE")
                    .stageName("路由决策")
                    .actionCode("HTTP_CALLBACK")
                    .actionName("业务系统 HTTP 接口回调")
                    .detail("POST http://api.fleet.internal/api/v1/driver/hotline/match (800ms 熔断保护)")
                    .status("SUCCESS")
                    .duration("120ms")
                    .build());
        }

        // Stage 3: CONNECTED
        traces.add(CallTraceStepVO.builder()
                .timeOffset("+00:02.0s")
                .stage("CONNECTED")
                .stageName("通话中")
                .actionCode("BRIDGE")
                .actionName("模拟通道桥接与双轨录音")
                .detail("FreeSWITCH uuid_bridge 桥接就绪，启动双轨录音")
                .status("SUCCESS")
                .build());

        // Stage 4: END
        traces.add(CallTraceStepVO.builder()
                .timeOffset("+00:05.0s")
                .stage("END")
                .stageName("结束阶段")
                .actionCode("POST_SURVEY")
                .actionName("满意度评价收尾")
                .detail("停止录音，播放满意度评价语音并释放通道")
                .status("SUCCESS")
                .build());

        return FlowSimulateRespVO.builder()
                .success(true)
                .simulationId(simId)
                .decisionResult(decisionResult)
                .targetAgentWorkNo(targetWorkNo)
                .targetAgentName(targetName)
                .traces(traces)
                .build();
    }

    private String calculateSha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "sha256-" + System.currentTimeMillis();
        }
    }
}
