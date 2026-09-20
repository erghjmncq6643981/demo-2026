package com.chandler.fcc.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.admin.model.dto.FlowPublishReq;
import com.chandler.fcc.admin.model.dto.FlowSaveDraftReq;
import com.chandler.fcc.admin.model.dto.FlowSimulateReq;
import com.chandler.fcc.admin.model.vo.FlowDefinitionVO;
import com.chandler.fcc.admin.model.vo.FlowSimulateRespVO;
import com.chandler.fcc.admin.model.vo.FlowVersionVO;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.common.protocol.FlowDefinitionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${fcc.server.base-url:}")
    private String fccServerBaseUrl;

    @Value("${fcc.flow.reload-token:}")
    private String reloadToken;

    /**
     * 查询所有流程列表及包含的版本信息
     */
    public List<FlowDefinitionVO> listFlows() {
        StpUtil.checkPermission("flow:view");
        List<FlowDefinitionEntity> entities = flowMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .isNull(FlowDefinitionEntity::getDeletedAt)
                .orderByAsc(FlowDefinitionEntity::getId)
        );

        List<FlowDefinitionVO> vos = new ArrayList<>();
        for (FlowDefinitionEntity e : entities) {
            List<FlowVersionVO> versions = getVersions(e.getFlowKey());
            String curVerStr = "v1." + (e.getCurrentVersion() != null ? e.getCurrentVersion() - 1 : 0) + ".0";
            if (e.getCurrentVersion() == 1) {
                curVerStr = "v1.0.0";
            }
            vos.add(
                FlowDefinitionVO.builder()
                    .id(e.getId())
                    .flowKey(e.getFlowKey())
                    .flowName(e.getFlowName())
                    .modelType(e.getModelType())
                    .status(e.getStatus())
                    .currentVersion(curVerStr)
                    .versions(versions)
                    .build()
            );
        }
        return vos;
    }

    /**
     * 获取指定流程的所有历史和草稿版本
     */
    public List<FlowVersionVO> getVersions(String flowKey) {
        StpUtil.checkPermission("flow:view");
        FlowDefinitionEntity flow = flowMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getFlowKey, flowKey)
        );
        if (flow == null) {
            return List.of();
        }

        List<FlowDefinitionVersionEntity> versionEntities = versionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .orderByDesc(FlowDefinitionVersionEntity::getVersionNo)
        );

        return versionEntities
            .stream()
            .map(v -> {
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
            })
            .toList();
    }

    /**
     * 保存流程草稿配置
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveDraft(String flowKey, FlowSaveDraftReq req) {
        if (flowKey.startsWith("SYSTEM_")) throw new IllegalArgumentException("系统固定模板不允许编辑");
        StpUtil.checkPermission("flow:write");
        FlowDefinitionEntity flow = flowMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getFlowKey, flowKey)
                .last("FOR UPDATE")
        );
        if (flow == null) {
            throw new IllegalArgumentException("流程定义不存在: " + flowKey);
        }

        LocalDateTime now = LocalDateTime.now();
        String json = req.getDefinitionJson() != null ? req.getDefinitionJson() : "{}";
        json = FlowDefinitionValidator.validate(json).toString();
        String checksum = calculateSha256(json);

        // 查找现有的草稿版本 (DRAFT)
        FlowDefinitionVersionEntity draft = versionMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT")
        );

        if (draft != null) {
            draft.setDefinitionJson(json);
            draft.setChecksum(checksum);
            versionMapper.updateById(draft);
            log.info(
                "[FlowDefinitionService] 更新流程草稿: flowKey={}, versionNo={}",
                flowKey,
                draft.getVersionNo()
            );
        } else {
            FlowDefinitionVersionEntity latest = versionMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                    .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                    .orderByDesc(FlowDefinitionVersionEntity::getVersionNo)
                    .last("LIMIT 1")
            );
            int nextVersionNo = latest == null || latest.getVersionNo() == null
                ? 1
                : latest.getVersionNo() + 1;
            draft = FlowDefinitionVersionEntity.builder()
                .id(IdUtil.nextId())
                .flowDefinitionId(flow.getId())
                .versionNo(nextVersionNo)
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

        return formatVersion(draft.getVersionNo());
    }

    /**
     * 正式发布版本上线
     */
    @Transactional(rollbackFor = Exception.class)
    public String publishFlow(String flowKey, FlowPublishReq req) {
        if (flowKey.startsWith("SYSTEM_")) throw new IllegalArgumentException("系统固定模板不允许发布修改");
        StpUtil.checkPermission("flow:write");
        FlowDefinitionEntity flow = flowMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getFlowKey, flowKey)
                .last("FOR UPDATE")
        );
        if (flow == null) {
            throw new IllegalArgumentException("流程定义不存在: " + flowKey);
        }

        LocalDateTime now = LocalDateTime.now();

        // 查找待发布的草稿
        FlowDefinitionVersionEntity draft = versionMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT")
        );

        if (draft == null) {
            throw new IllegalStateException("没有可发布的草稿版本");
        }
        if (!formatVersion(draft.getVersionNo()).equals(req.getVersion())) {
            throw new IllegalArgumentException("待发布版本已变化，请刷新后重试");
        }

        FlowDefinitionValidator.validate(draft.getDefinitionJson());

        // 将之前 PUBLISHED 的版本归档 ARCHIVED
        List<FlowDefinitionVersionEntity> oldPubs = versionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "PUBLISHED")
        );
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
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                /**
                 * 数据提交后再通知运行端，避免读取旧版本。
                 */
                @Override
                public void afterCommit() {
                    notifyEngineReload(flowKey);
                }
            }
        );

        String publishedVersion = formatVersion(draft.getVersionNo());
        log.info(
            "[FlowDefinitionService] 流程版本更新已登记，提交后通知运行端: flowKey={}, version={}",
            flowKey,
            publishedVersion
        );
        return publishedVersion;
    }

    /**
     * 向运行时控制服务发送流程重载通知。
     *
     * @param flowKey 流程唯一键
     */
    private void notifyEngineReload(String flowKey) {
        // 1. Redis 广播通知
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.convertAndSend("fcc:flow:publish", flowKey);
                log.info(
                    "📢 [FlowDefinitionService] 已广播流程发布事件至 Redis: fcc:flow:publish, flowKey={}",
                    flowKey
                );
            } catch (Exception re) {
                log.warn("⚠️ [FlowDefinitionService] Redis 广播发布事件略过: {}", re.getMessage());
            }
        }
        if (fccServerBaseUrl == null || fccServerBaseUrl.isBlank() || reloadToken.isBlank()) {
            log.warn(
                "[FlowDefinitionService] 未配置 fcc.server.base-url，仅完成版本发布与 Redis 通知: flowKey={}",
                flowKey
            );
            return;
        }

        // 2. HTTP 异步通知 fcc-server
        try {
            String encodedFlowKey = URLEncoder.encode(flowKey, StandardCharsets.UTF_8);
            String baseUrl = fccServerBaseUrl.replaceAll("/+$", "");
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(
                    URI.create(baseUrl + "/api/telephony/call/flow/reload?flowKey=" + encodedFlowKey)
                )
                .header("X-FCC-Reload-Token", reloadToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofMillis(1500))
                .build();
            client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, failure) -> {
                    if (failure != null) {
                        log.warn("[流程发布] 运行端重载未确认: flowKey={}", flowKey);
                        return;
                    }
                    try {
                        var result = new ObjectMapper().readTree(response.body());
                        if (
                            response.statusCode() != 200 ||
                            result.path("code").asInt() != 200 ||
                            !result.path("data").path("reloaded").asBoolean()
                        ) {
                            log.warn("[流程发布] 运行端拒绝激活: flowKey={}", flowKey);
                        } else {
                            log.info("[流程发布] 运行端已确认重载: flowKey={}", flowKey);
                        }
                    } catch (Exception ignored) {
                        log.warn("[流程发布] 运行端响应无法确认: flowKey={}", flowKey);
                    }
                });
        } catch (Exception httpEx) {
            log.warn("⚠️ [FlowDefinitionService] HTTP 热重载通知略过: {}", httpEx.getMessage());
        }
    }

    /**
     * 将数据库版本序号格式化为公开版本号。
     *
     * @param versionNo 数据库递增版本序号
     * @return 公开版本号
     */
    private String formatVersion(Integer versionNo) {
        int safeVersion = versionNo == null || versionNo < 1 ? 1 : versionNo;
        return "v1." + (safeVersion - 1) + ".0";
    }

    /**
     * 查询流程仿真能力。
     *
     * <p>当前尚未接入与生产执行器共用的仿真引擎，因此明确返回不可用，
     * 不生成与真实路由无关的成功轨迹。</p>
     *
     * @param req 仿真输入
     * @return 明确标记为失败的能力状态
     */
    public FlowSimulateRespVO simulateFlow(FlowSimulateReq req) {
        return FlowSimulateRespVO.builder()
            .success(false)
            .simulationId(null)
            .decisionResult("流程仿真引擎尚未接入")
            .targetAgentWorkNo(null)
            .targetAgentName(null)
            .traces(List.of())
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
