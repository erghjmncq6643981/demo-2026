package com.chandler.fcc.admin.flow.application;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.flow.controller.req.CreateFlowReq;
import com.chandler.fcc.admin.flow.controller.req.FlowPageReq;
import com.chandler.fcc.admin.flow.controller.req.PublishFlowReq;
import com.chandler.fcc.admin.flow.controller.req.SaveFlowDraftReq;
import com.chandler.fcc.admin.flow.controller.resp.FlowActionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionInstanceResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowExecutionStepResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowPublishResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowSummaryResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowVersionResp;
import com.chandler.fcc.admin.flow.controller.resp.SystemFlowModelResp;
import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionInstanceData;
import com.chandler.fcc.admin.flow.infrastructure.data.FlowExecutionStepData;
import com.chandler.fcc.admin.infrastructure.persistence.entity.DidNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FlowDefinitionValidator;
import com.chandler.fcc.common.protocol.SystemFlowModels;
import com.chandler.fcc.common.util.IdUtil;
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
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * IVR Flow Studio 的模型目录、流程版本维护和执行轨迹应用服务。
 *
 * <p>系统模型与动作目录来自 fcc-common；可编辑流程只保存受公共验证器支持的参数，
 * 发布事务提交后才通知运行端加载，新版本不会改变正在执行的通话快照。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowStudioService {

    private static final String SYSTEM_PREFIX = "SYSTEM_";
    private static final String FLOW_PUBLISH_CHANNEL = "fcc:flow:publish";

    private final FlowDefinitionMapper flowMapper;
    private final FlowDefinitionVersionMapper versionMapper;
    private final DidNumberMapper didNumberMapper;
    private final FlowStudioMapper executionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private volatile HttpClient httpClient;

    @Value("${fcc.server.base-url:}")
    private String fccServerBaseUrl;

    @Value("${fcc.flow.reload-token:}")
    private String reloadToken;

    /**
     * 返回管理画布使用的公共动作和执行器目录。
     *
     * @return 稳定动作目录
     */
    public List<FlowActionResp> actions() {
        StpUtil.checkPermission("flow:view");
        return List.of(FlowActionType.values())
            .stream()
            .map(action -> FlowActionResp.builder()
                .code(action.name())
                .label(action.getDesc())
                .executorType(action.getExecutorType().name())
                .executorTypeLabel(action.getExecutorType().getDesc())
                .operation(action.getOperation())
                .fNodeMethod(action.getFNodeMethod() == null ? null : action.getFNodeMethod().getWireName())
                .build())
            .toList();
    }

    /**
     * 返回随应用部署的完整固定通话模型。
     *
     * @return 按模板代码排序的模型目录
     */
    public List<SystemFlowModelResp> systemModels() {
        StpUtil.checkPermission("flow:view");
        return SystemFlowModels.templateNames()
            .stream()
            .sorted()
            .map(template -> systemModel(template))
            .toList();
    }

    /**
     * 返回一个固定通话模型详情。
     *
     * @param template 固定模板代码
     * @return 完整模型定义
     */
    public SystemFlowModelResp systemModel(String template) {
        StpUtil.checkPermission("flow:view");
        return SystemFlowModelResp.builder()
            .template(template)
            .definition(SystemFlowModels.get(template))
            .build();
    }

    /**
     * 分页量较小的流程主数据摘要列表，不加载版本 JSON。
     *
     * @return 流程摘要列表
     */
    public PageResult<FlowSummaryResp> listFlows(FlowPageReq request) {
        StpUtil.checkPermission("flow:view");
        Page<FlowDefinitionEntity> page = flowMapper.selectPage(
            new Page<>(request.getPageNum(), request.getPageSize()),
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getModelType, "INBOUND")
                .isNull(FlowDefinitionEntity::getDeletedAt)
                .orderByDesc(FlowDefinitionEntity::getId)
        );
        return PageResult.<FlowSummaryResp>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .list(page.getRecords().stream().map(this::summary).toList())
            .build();
    }

    /**
     * 查询单个流程主数据详情。
     *
     * @param flowKey 稳定流程代码
     * @return 流程摘要
     */
    public FlowSummaryResp flow(String flowKey) {
        StpUtil.checkPermission("flow:view");
        return summary(requireFlow(flowKey, false));
    }

    /**
     * 创建一个不含虚构路由数据的新呼入流程。
     *
     * @param request 创建参数
     * @return 新流程摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowSummaryResp create(CreateFlowReq request) {
        StpUtil.checkPermission("flow:write");
        String flowKey = request.getFlowKey() == null ? "" : request.getFlowKey().trim();
        String flowName = request.getFlowName() == null ? "" : request.getFlowName().trim();
        if (!flowKey.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || flowKey.startsWith(SYSTEM_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程代码不合法");
        }
        if (flowName.isBlank() || flowName.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程名称不合法");
        }
        if (findFlow(flowKey) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "流程代码已经存在");
        }
        LocalDateTime now = LocalDateTime.now();
        FlowDefinitionEntity entity = FlowDefinitionEntity.builder()
            .id(IdUtil.nextId())
            .flowKey(flowKey)
            .flowName(flowName)
            .modelType("INBOUND")
            .status("DRAFT")
            .currentVersion(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
        flowMapper.insert(entity);
        log.info("[流程管理] 新建流程 flowKey={}", flowKey);
        return summary(entity);
    }

    /**
     * 查询流程的轻量版本列表，不返回完整定义 JSON。
     *
     * @param flowKey 稳定流程代码
     * @return 按版本倒序排列的摘要
     */
    public PageResult<FlowVersionResp> versions(String flowKey, FlowPageReq request) {
        StpUtil.checkPermission("flow:view");
        FlowDefinitionEntity flow = requireFlow(flowKey, false);
        Page<FlowDefinitionVersionEntity> page = versionMapper.selectPage(
            new Page<>(request.getPageNum(), request.getPageSize()),
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .orderByDesc(FlowDefinitionVersionEntity::getVersionNo)
        );
        return PageResult.<FlowVersionResp>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .list(page.getRecords().stream().map(version -> version(version, false)).toList())
            .build();
    }

    /**
     * 按需加载一个流程版本的完整定义。
     *
     * @param flowKey 稳定流程代码
     * @param versionNo 递增版本序号
     * @return 版本详情
     */
    public FlowVersionResp version(String flowKey, int versionNo) {
        StpUtil.checkPermission("flow:view");
        if (versionNo < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本序号不合法");
        }
        FlowDefinitionEntity flow = requireFlow(flowKey, false);
        FlowDefinitionVersionEntity entity = versionMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getVersionNo, versionNo)
        );
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "流程版本不存在");
        }
        return version(entity, true);
    }

    /**
     * 保存或更新唯一草稿版本。
     *
     * @param flowKey 稳定流程代码
     * @param request 已提交的完整定义
     * @return 保存后的版本详情
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowVersionResp saveDraft(String flowKey, SaveFlowDraftReq request) {
        StpUtil.checkPermission("flow:write");
        FlowDefinitionEntity flow = requireFlow(flowKey, true);
        rejectSystemFlow(flowKey);
        String definition = FlowDefinitionValidator.validate(request.getDefinitionJson()).toString();
        List<FlowDefinitionVersionEntity> drafts = versionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT")
        );
        if (drafts.size() > 1) {
            throw new IllegalStateException("流程存在多个草稿版本，需要先修复数据");
        }
        LocalDateTime now = LocalDateTime.now();
        FlowDefinitionVersionEntity draft = drafts.isEmpty() ? null : drafts.getFirst();
        if (draft == null) {
            FlowDefinitionVersionEntity latest = versionMapper.selectOne(
                new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                    .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                    .orderByDesc(FlowDefinitionVersionEntity::getVersionNo)
                    .last("LIMIT 1")
            );
            int nextVersionNo = latest == null ? 1 : latest.getVersionNo() + 1;
            draft = FlowDefinitionVersionEntity.builder()
                .id(IdUtil.nextId())
                .flowDefinitionId(flow.getId())
                .versionNo(nextVersionNo)
                .definitionJson(definition)
                .checksum(sha256(definition))
                .publishStatus("DRAFT")
                .createdBy(currentActor())
                .createdAt(now)
                .build();
            versionMapper.insert(draft);
        } else {
            draft.setDefinitionJson(definition);
            draft.setChecksum(sha256(definition));
            versionMapper.updateById(draft);
        }
        flow.setUpdatedAt(now);
        flowMapper.updateById(flow);
        log.info("[流程管理] 保存草稿 flowKey={}, version={}", flowKey, formatVersion(draft.getVersionNo()));
        return version(draft, true);
    }

    /**
     * 发布唯一草稿版本并在事务提交后通知运行端。
     *
     * @param flowKey 稳定流程代码
     * @param request 发布参数
     * @return 数据库发布状态与运行端待确认状态
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowPublishResp publish(String flowKey, PublishFlowReq request) {
        StpUtil.checkPermission("flow:write");
        FlowDefinitionEntity flow = requireFlow(flowKey, true);
        rejectSystemFlow(flowKey);
        requireInboundDidBinding(flow);
        List<FlowDefinitionVersionEntity> drafts = versionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT")
        );
        if (drafts.size() != 1) {
            throw new IllegalStateException(drafts.isEmpty() ? "没有可发布的草稿版本" : "流程存在多个草稿版本");
        }
        FlowDefinitionVersionEntity draft = drafts.getFirst();
        if (!formatVersion(draft.getVersionNo()).equals(request.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "待发布版本已变化，请刷新后重试");
        }
        FlowDefinitionValidator.validate(draft.getDefinitionJson());
        versionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "PUBLISHED")
        ).forEach(published -> {
            published.setPublishStatus("ARCHIVED");
            versionMapper.updateById(published);
        });
        LocalDateTime now = LocalDateTime.now();
        draft.setPublishStatus("PUBLISHED");
        draft.setPublishedAt(now);
        versionMapper.updateById(draft);
        flow.setCurrentVersion(draft.getVersionNo());
        flow.setStatus("PUBLISHED");
        flow.setUpdatedAt(now);
        flowMapper.updateById(flow);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 数据提交后通知运行端，避免运行端读到旧版本。
             */
            @Override
            public void afterCommit() {
                notifyRuntime(flowKey);
            }
        });
        String version = formatVersion(draft.getVersionNo());
        log.info("[流程管理] 发布版本 flowKey={}, version={}", flowKey, version);
        return FlowPublishResp.builder()
            .version(version)
            .publishStatus("PUBLISHED")
            .runtimeActivationStatus("PENDING")
            .build();
    }

    /**
     * 校验呼入流程至少有一个启用的 DID 入口。
     *
     * @param flow 待发布流程主数据
     * @throws ResponseStatusException 呼入流程没有任何可达入口
     */
    private void requireInboundDidBinding(FlowDefinitionEntity flow) {
        if (!"INBOUND".equals(flow.getModelType())) {
            return;
        }
        Long count = didNumberMapper.selectCount(
            new LambdaQueryWrapper<DidNumberEntity>()
                .eq(DidNumberEntity::getRouteKey, flow.getFlowKey())
                .eq(DidNumberEntity::getStatus, "ENABLED")
                .isNull(DidNumberEntity::getDeletedAt)
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先绑定至少一个被叫号码再发布流程");
        }
    }

    /**
     * 读取一通电话固定的模型快照和按游标分页的动作事实。
     *
     * @param callId 业务通话标识
     * @param after 上一页末尾记录标识
     * @return 执行轨迹
     */
    public FlowExecutionResp execution(String callId, String after) {
        StpUtil.checkPermission("flow:view");
        if (!callId.matches("[1-9][0-9]{0,18}") || !after.matches("[0-9]{1,19}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标识不合法");
        }
        if (executionMapper.callExists(callId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "通话不存在");
        }
        var instance = executionMapper.instance(callId);
        var steps = executionMapper.steps(callId, after);
        FlowExecutionResp response = new FlowExecutionResp();
        response.setInstance(executionInstance(instance));
        response.setSteps(steps.stream().map(this::executionStep).toList());
        response.setNextCursor(steps.size() == 100 ? steps.getLast().getId() : "");
        return response;
    }

    /**
     * 将持久化投影转换为自解释的流程实例响应。
     *
     * @param data 数据库投影，无流程实例时为空
     * @return 流程实例响应
     */
    private FlowExecutionInstanceResp executionInstance(FlowExecutionInstanceData data) {
        if (data == null) {
            return FlowExecutionInstanceResp.builder().build();
        }
        return FlowExecutionInstanceResp.builder()
            .id(data.getId())
            .callId(data.getCallId())
            .versionId(data.getVersionId())
            .status(data.getStatus())
            .currentStep(data.getCurrentStep())
            .snapshot(data.getSnapshot())
            .startedAt(data.getStartedAt())
            .endedAt(data.getEndedAt())
            .build();
    }

    /**
     * 将持久化投影转换为自解释的阶段执行响应。
     *
     * @param data 阶段执行数据库投影
     * @return 阶段执行响应
     */
    private FlowExecutionStepResp executionStep(FlowExecutionStepData data) {
        return FlowExecutionStepResp.builder()
            .id(data.getId())
            .stepKey(data.getStepKey())
            .actionType(data.getActionType())
            .attemptNo(data.getAttemptNo())
            .status(data.getStatus())
            .commandId(data.getCommandId())
            .eventId(data.getEventId())
            .input(data.getInput())
            .output(data.getOutput())
            .errorCode(data.getErrorCode())
            .startedAt(data.getStartedAt())
            .endedAt(data.getEndedAt())
            .durationMs(data.getDurationMs())
            .build();
    }

    /**
     * 查询并可选锁定流程主数据。
     *
     * @param flowKey 稳定流程代码
     * @param lock 是否追加数据库行锁
     * @return 流程实体
     */
    private FlowDefinitionEntity requireFlow(String flowKey, boolean lock) {
        if (flowKey == null || !flowKey.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "流程代码不合法");
        }
        LambdaQueryWrapper<FlowDefinitionEntity> query = new LambdaQueryWrapper<FlowDefinitionEntity>()
            .eq(FlowDefinitionEntity::getFlowKey, flowKey)
            .isNull(FlowDefinitionEntity::getDeletedAt);
        if (lock) {
            query.last("FOR UPDATE");
        }
        FlowDefinitionEntity flow = flowMapper.selectOne(query);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "流程定义不存在");
        }
        return flow;
    }

    /**
     * 查询流程主数据，不存在时返回空。
     *
     * @param flowKey 稳定流程代码
     * @return 流程实体或空
     */
    private FlowDefinitionEntity findFlow(String flowKey) {
        return flowMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getFlowKey, flowKey)
                .isNull(FlowDefinitionEntity::getDeletedAt)
        );
    }

    /**
     * 将主数据转换为轻量摘要。
     *
     * @param entity 流程实体
     * @return 流程摘要
     */
    private FlowSummaryResp summary(FlowDefinitionEntity entity) {
        Integer current = entity.getCurrentVersion();
        return FlowSummaryResp.builder()
            .id(entity.getId().toString())
            .flowKey(entity.getFlowKey())
            .flowName(entity.getFlowName())
            .modelType(entity.getModelType())
            .status(entity.getStatus())
            .currentVersion(current == null || current < 1 ? null : formatVersion(current))
            .system(entity.getFlowKey().startsWith(SYSTEM_PREFIX))
            .build();
    }

    /**
     * 将版本实体转换为摘要或详情。
     *
     * @param entity 版本实体
     * @param includeDefinition 是否返回完整定义
     * @return 版本响应
     */
    private FlowVersionResp version(FlowDefinitionVersionEntity entity, boolean includeDefinition) {
        return FlowVersionResp.builder()
            .id(entity.getId().toString())
            .version(formatVersion(entity.getVersionNo()))
            .versionNo(entity.getVersionNo())
            .publishStatus(entity.getPublishStatus())
            .definitionJson(includeDefinition ? entity.getDefinitionJson() : null)
            .publishedAt(entity.getPublishedAt())
            .createdBy(entity.getCreatedBy())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    /**
     * 拒绝修改随应用发布的系统模型。
     *
     * @param flowKey 稳定流程代码
     */
    private void rejectSystemFlow(String flowKey) {
        if (flowKey.startsWith(SYSTEM_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统固定模型不允许修改");
        }
    }

    /**
     * 返回当前管理账号标识。
     *
     * @return 创建人账号
     */
    private String currentActor() {
        Object actor = StpUtil.getLoginIdDefaultNull();
        return actor == null ? "unknown" : actor.toString();
    }

    /**
     * 计算流程定义内容校验和。
     *
     * @param source 规范定义 JSON
     * @return SHA-256 十六进制文本
     */
    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("无法计算流程定义校验和", failure);
        }
    }

    /**
     * 将数据库版本序号格式化为公开版本号。
     *
     * @param versionNo 数据库递增版本序号
     * @return 公开版本号
     */
    private String formatVersion(Integer versionNo) {
        if (versionNo == null || versionNo < 1) {
            throw new IllegalArgumentException("版本序号不合法");
        }
        return "v1." + (versionNo - 1) + ".0";
    }

    /**
     * 发布提交后广播变更并异步请求运行端重载。
     *
     * @param flowKey 稳定流程代码
     */
    private void notifyRuntime(String flowKey) {
        try {
            redisTemplate.convertAndSend(FLOW_PUBLISH_CHANNEL, flowKey);
        } catch (Exception failure) {
            log.warn("[流程发布] Redis 广播未确认 flowKey={}", flowKey);
        }
        if (fccServerBaseUrl == null || fccServerBaseUrl.isBlank() || reloadToken.isBlank()) {
            log.warn("[流程发布] 未配置运行端重载地址 flowKey={}", flowKey);
            return;
        }
        try {
            String baseUrl = fccServerBaseUrl.replaceAll("/+$", "");
            String encoded = URLEncoder.encode(flowKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/telephony/call/flow/reload?flowKey=" + encoded))
                .header("X-FCC-Reload-Token", reloadToken)
                .timeout(Duration.ofMillis(1500))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            httpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) -> {
                if (failure != null || response.statusCode() != 200) {
                    log.warn("[流程发布] 运行端重载未确认 flowKey={}", flowKey);
                    return;
                }
                try {
                    boolean reloaded = objectMapper.readTree(response.body())
                        .path("data")
                        .path("reloaded")
                        .asBoolean(false);
                    if (reloaded) {
                        log.info("[流程发布] 运行端已确认重载 flowKey={}", flowKey);
                    } else {
                        log.warn("[流程发布] 运行端未确认激活 flowKey={}", flowKey);
                    }
                } catch (Exception invalidResponse) {
                    log.warn("[流程发布] 运行端响应无法解析 flowKey={}", flowKey);
                }
            });
        } catch (Exception failure) {
            log.warn("[流程发布] 运行端通知未发出 flowKey={}", flowKey);
        }
    }

    /**
     * 在首次发布通知时才创建 HTTP 客户端，避免未使用运行端通知时影响服务启动。
     *
     * @return 可复用的 HTTP 客户端
     */
    private HttpClient httpClient() {
        HttpClient current = httpClient;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newHttpClient();
            }
            return httpClient;
        }
    }
}
