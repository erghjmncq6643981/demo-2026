package com.chandler.fcc.admin.flow.application;

import static com.chandler.fcc.admin.flow.application.assembler.FlowResponseAssembler.formatVersion;
import static com.chandler.fcc.admin.flow.application.assembler.FlowResponseAssembler.summary;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.flow.controller.req.CreateFlowReq;
import com.chandler.fcc.admin.flow.controller.req.FlowPageReq;
import com.chandler.fcc.admin.flow.controller.req.PublishFlowReq;
import com.chandler.fcc.admin.flow.controller.req.SaveFlowDraftReq;
import com.chandler.fcc.admin.flow.controller.resp.FlowActionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowPublishResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowSummaryResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowTypeResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowVersionResp;
import com.chandler.fcc.admin.flow.controller.resp.FlowValidationResp;
import com.chandler.fcc.admin.flow.controller.resp.SystemFlowModelResp;
import com.chandler.fcc.admin.flow.application.assembler.FlowResponseAssembler;
import com.chandler.fcc.admin.flow.application.coordinator.FlowRuntimeNotifier;
import com.chandler.fcc.admin.flow.application.policy.FlowPublicationPolicy;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionVersionMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.enums.FlowTemplateType;
import com.chandler.fcc.common.protocol.FlowDefinitionValidator;
import com.chandler.fcc.common.protocol.StagedFlowDefinition;
import com.chandler.fcc.common.protocol.SystemFlowModels;
import com.chandler.fcc.common.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Flow Studio 的模型目录、草稿维护及发布事务应用服务。
 *
 * <p>系统模型与动作目录来自 fcc-common；可编辑流程只保存受公共验证器支持的参数，
 * 发布事务提交后才通知运行端加载，新版本不会改变正在执行的通话快照。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowStudioService {

    private static final String SYSTEM_PREFIX = "SYSTEM_";

    private final FlowDefinitionMapper flowMapper;
    private final FlowDefinitionVersionMapper versionMapper;
    private final FlowPublicationPolicy publicationPolicy;
    private final FlowRuntimeNotifier runtimeNotifier;

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
     * 返回当前真正具备管理和运行闭环的可创建流程类型。
     *
     * @return 可创建流程类型目录
     */
    public List<FlowTypeResp> types() {
        StpUtil.checkPermission("flow:view");
        return List.of(FlowTemplateType.values())
            .stream()
            .map(type -> FlowTypeResp.builder()
                .code(type.name())
                .label(type.getDesc())
                .description(type.getBusinessDescription())
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
                .eq(FlowDefinitionEntity::getModelType, FlowTemplateType.INBOUND.name())
                .notIn(
                    FlowDefinitionEntity::getFlowKey,
                    SystemFlowModels.templateNames()
                        .stream()
                        .map(template -> SYSTEM_PREFIX + template)
                        .toList()
                )
                .isNull(FlowDefinitionEntity::getDeletedAt)
                .orderByDesc(FlowDefinitionEntity::getId)
        );
        return PageResult.<FlowSummaryResp>builder()
            .pageNum(page.getCurrent())
            .pageSize(page.getSize())
            .total(page.getTotal())
            .list(page.getRecords().stream().map(FlowResponseAssembler::summary).toList())
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
     * 创建流程主数据及与类型匹配的首个草稿版本。
     *
     * <p>主数据和草稿在同一事务内写入，草稿只包含固定结构，不填充虚构业务数据。</p>
     *
     * @param request 创建参数
     * @return 新流程摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowSummaryResp create(CreateFlowReq request) {
        StpUtil.checkPermission("flow:write");
        String flowKey = request.getFlowKey() == null ? "" : request.getFlowKey().trim();
        String flowName = request.getFlowName() == null ? "" : request.getFlowName().trim();
        FlowTemplateType modelType = request.getModelType();
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
            .modelType(modelType.name())
            .status("DRAFT")
            .currentVersion(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
        flowMapper.insert(entity);
        String definition = StagedFlowDefinition.initialDraft(modelType).toString();
        FlowDefinitionVersionEntity draft = FlowDefinitionVersionEntity.builder()
            .id(IdUtil.nextId())
            .flowDefinitionId(entity.getId())
            .versionNo(1)
            .definitionJson(definition)
            .checksum(sha256(definition))
            .publishStatus("DRAFT")
            .createdBy(currentActor())
            .createdAt(now)
            .build();
        versionMapper.insert(draft);
        log.info(
            "[流程管理] 新建流程及首个草稿 flowKey={}, modelType={}, version={}",
            flowKey,
            modelType.name(),
            formatVersion(draft.getVersionNo())
        );
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
            .list(page.getRecords().stream().map(version -> FlowResponseAssembler.version(version, false)).toList())
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
        return FlowResponseAssembler.version(entity, true);
    }

    /**
     * 从指定已发布或历史版本派生唯一草稿，实现已发布流程的写时复制编辑。
     *
     * <p>已有草稿时幂等返回该草稿，不修改当前生效版本；新草稿使用下一个递增版本号。</p>
     *
     * @param flowKey 稳定流程代码
     * @param versionNo 作为草稿来源的版本序号
     * @return 已有或新建的唯一草稿详情
     * @throws ResponseStatusException 来源版本不存在、尚未发布或流程不可编辑
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowVersionResp createDraftFromVersion(String flowKey, int versionNo) {
        StpUtil.checkPermission("flow:write");
        if (versionNo < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本序号不合法");
        }
        FlowDefinitionEntity flow = requireFlow(flowKey, true);
        rejectSystemFlow(flowKey);
        List<FlowDefinitionVersionEntity> drafts = versionMapper.selectList(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getPublishStatus, "DRAFT")
        );
        if (drafts.size() > 1) {
            throw new IllegalStateException("流程存在多个草稿版本，需要先修复数据");
        }
        if (!drafts.isEmpty()) {
            FlowDefinitionVersionEntity existing = drafts.getFirst();
            log.info(
                "[流程管理] 复用已有草稿 flowKey={}, version={}",
                flowKey,
                formatVersion(existing.getVersionNo())
            );
            return FlowResponseAssembler.version(existing, true);
        }

        FlowDefinitionVersionEntity source = versionMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .eq(FlowDefinitionVersionEntity::getVersionNo, versionNo)
        );
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "流程版本不存在");
        }
        if ("DRAFT".equals(source.getPublishStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "草稿版本可以直接编辑，无需再次派生");
        }
        String definition = FlowDefinitionValidator.normalizeDraft(
            source.getDefinitionJson(),
            flow.getModelType()
        ).toString();
        FlowDefinitionVersionEntity latest = versionMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionVersionEntity>()
                .eq(FlowDefinitionVersionEntity::getFlowDefinitionId, flow.getId())
                .orderByDesc(FlowDefinitionVersionEntity::getVersionNo)
                .last("LIMIT 1")
        );
        int nextVersionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        FlowDefinitionVersionEntity draft = FlowDefinitionVersionEntity.builder()
            .id(IdUtil.nextId())
            .flowDefinitionId(flow.getId())
            .versionNo(nextVersionNo)
            .definitionJson(definition)
            .checksum(sha256(definition))
            .publishStatus("DRAFT")
            .createdBy(currentActor())
            .createdAt(LocalDateTime.now())
            .build();
        versionMapper.insert(draft);
        flow.setUpdatedAt(LocalDateTime.now());
        flowMapper.updateById(flow);
        log.info(
            "[流程管理] 从历史版本派生草稿 flowKey={}, sourceVersion={}, draftVersion={}",
            flowKey,
            formatVersion(source.getVersionNo()),
            formatVersion(draft.getVersionNo())
        );
        return FlowResponseAssembler.version(draft, true);
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
        String definition = FlowDefinitionValidator.normalizeDraft(
            request.getDefinitionJson(),
            flow.getModelType()
        ).toString();
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
        return FlowResponseAssembler.version(draft, true);
    }

    /**
     * 使用管理端与运行端共享的严格契约校验草稿，不产生版本或其他持久化副作用。
     *
     * @param flowKey 稳定流程代码
     * @param request 待校验完整定义
     * @return 规范化定义
     */
    public FlowValidationResp validate(String flowKey, SaveFlowDraftReq request) {
        StpUtil.checkPermission("flow:write");
        FlowDefinitionEntity flow = requireFlow(flowKey, true);
        rejectSystemFlow(flowKey);
        String normalized = FlowDefinitionValidator.validate(
            request.getDefinitionJson(),
            flow.getModelType()
        ).toString();
        return FlowValidationResp.builder()
            .valid(true)
            .normalizedDefinitionJson(normalized)
            .build();
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
        publicationPolicy.requireInboundDidBinding(flow);
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
        JsonNode normalized = FlowDefinitionValidator.validate(
            draft.getDefinitionJson(),
            flow.getModelType()
        );
        publicationPolicy.requireInboundRouteTargets(flow, normalized);
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
                runtimeNotifier.notifyRuntime(flowKey);
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
}
