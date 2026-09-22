package com.chandler.fcc.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.DidNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.OutboundNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.TelephonyNodeEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.OutboundNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.TelephonyNodeMapper;
import com.chandler.fcc.admin.model.dto.DidNumberCreateReq;
import com.chandler.fcc.admin.model.dto.OutboundNumberCreateReq;
import com.chandler.fcc.admin.model.vo.DidNumberVO;
import com.chandler.fcc.admin.model.vo.OutboundNumberVO;
import com.chandler.fcc.admin.model.vo.TelephonyNodeVO;
import com.chandler.fcc.common.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通信资源管理业务服务
 * <p>
 * 统一管理呼入 DID 引示号、外呼主叫号池及 FreeSWITCH 通信节点集群状态。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelephonyResourceService {

    private final DidNumberMapper didMapper;
    private final FlowDefinitionMapper flowMapper;
    private final OutboundNumberMapper outboundMapper;
    private final TelephonyNodeMapper nodeMapper;

    /**
     * 录入呼入 DID 引示号
     *
     * @param req DID 创建入参
     * @return DID 主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDidNumber(DidNumberCreateReq req) {
        String phone = req.getPhoneNumber().trim();
        String routingContext = normalizeRoutingContext(req.getRoutingContext());
        LambdaQueryWrapper<DidNumberEntity> check = new LambdaQueryWrapper<DidNumberEntity>()
                .isNull(DidNumberEntity::getDeletedAt)
                .eq(DidNumberEntity::getRoutingContext, routingContext)
                .eq(DidNumberEntity::getPhoneNumber, phone);
        if (didMapper.selectCount(check) > 0) {
            throw new IllegalArgumentException("DID号码已存在: " + phone);
        }

        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        DidNumberEntity entity = DidNumberEntity.builder()
                .id(id)
                .routingContext(routingContext)
                .phoneNumber(phone)
                .routeKey(req.getRouteKey())
                .status("ENABLED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        didMapper.insert(entity);
        log.info("[TelephonyResourceService] 录入DID号码成功: id={}, phone={}", id, phone);
        return id;
    }

    /**
     * 查询全量 DID 号码列表
     *
     * @return DID 视图列表
     */
    public List<DidNumberVO> listDidNumbers() {
        List<DidNumberEntity> dids = didMapper.selectList(
                new LambdaQueryWrapper<DidNumberEntity>()
                        .isNull(DidNumberEntity::getDeletedAt)
                        .orderByAsc(DidNumberEntity::getPhoneNumber));

        return dids.stream().map(d -> {
            return DidNumberVO.builder()
                    .id(d.getId())
                    .phoneNumber(d.getPhoneNumber())
                    .routingContext(d.getRoutingContext())
                    .routeKey(d.getRouteKey())
                    .status(d.getStatus())
                    .createdAt(d.getCreatedAt())
                    .build();
        }).toList();
    }

    /**
     * 将一个有效 DID 被叫号码绑定到呼入流程。
     *
     * <p>绑定属于入口路由配置，不进入流程版本 JSON。运行端收到 Channel 事件后按真实被叫号码
     * 查询该绑定，并固定当时最新的已发布版本。</p>
     *
     * @param didId DID 主键 ID
     * @param flowKey 呼入流程稳定代码
     * @throws IllegalArgumentException DID 或流程不存在，或目标不是呼入流程
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindDidFlow(Long didId, String flowKey) {
        StpUtil.checkPermission("resource:write");
        DidNumberEntity did = requireEnabledDid(didId);
        String normalizedFlowKey = flowKey == null ? "" : flowKey.trim();
        FlowDefinitionEntity flow = flowMapper.selectOne(
            new LambdaQueryWrapper<FlowDefinitionEntity>()
                .eq(FlowDefinitionEntity::getFlowKey, normalizedFlowKey)
                .eq(FlowDefinitionEntity::getModelType, "INBOUND")
                .isNull(FlowDefinitionEntity::getDeletedAt)
        );
        if (flow == null) {
            throw new IllegalArgumentException("呼入流程不存在: " + normalizedFlowKey);
        }
        did.setRouteKey(normalizedFlowKey);
        did.setUpdatedAt(LocalDateTime.now());
        didMapper.updateById(did);
        log.info("[通信资源] 已绑定DID呼入流程 didId={}, flowKey={}", didId, normalizedFlowKey);
    }

    /**
     * 解除 DID 被叫号码的流程绑定。
     *
     * @param didId DID 主键 ID
     * @throws IllegalArgumentException DID 不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbindDidFlow(Long didId) {
        StpUtil.checkPermission("resource:write");
        DidNumberEntity did = requireDid(didId);
        did.setRouteKey(null);
        did.setUpdatedAt(LocalDateTime.now());
        didMapper.updateById(did);
        log.info("[通信资源] 已解除DID呼入流程绑定 didId={}", didId);
    }

    /**
     * 查询一个仍未删除的 DID。
     *
     * @param didId DID 主键 ID
     * @return DID 持久化实体
     * @throws IllegalArgumentException DID 不存在
     */
    private DidNumberEntity requireDid(Long didId) {
        DidNumberEntity did = didId == null ? null : didMapper.selectOne(
            new LambdaQueryWrapper<DidNumberEntity>()
                .eq(DidNumberEntity::getId, didId)
                .isNull(DidNumberEntity::getDeletedAt)
        );
        if (did == null) {
            throw new IllegalArgumentException("DID号码不存在: id=" + didId);
        }
        return did;
    }

    /**
     * 查询一个可建立新呼入绑定的启用 DID。
     *
     * @param didId DID 主键 ID
     * @return 启用的 DID 持久化实体
     * @throws IllegalArgumentException DID 不存在或已停用
     */
    private DidNumberEntity requireEnabledDid(Long didId) {
        DidNumberEntity did = requireDid(didId);
        if (!"ENABLED".equals(did.getStatus())) {
            throw new IllegalArgumentException("DID号码已停用: id=" + didId);
        }
        return did;
    }

    /**
     * 删除 DID 号码
     *
     * @param id DID 主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDidNumber(Long id) {
        DidNumberEntity did = didMapper.selectById(id);
        if (did != null) {
            did.setStatus("DISABLED");
            did.setDeletedAt(LocalDateTime.now());
            didMapper.updateById(did);
            log.info("[TelephonyResourceService] 成功软删除DID号码: id={}", id);
        }
    }

    /**
     * 录入外呼主叫展示号码
     *
     * @param req 外呼主叫号码入参
     * @return 外呼号码主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createOutboundNumber(OutboundNumberCreateReq req) {
        String phone = req.getPhoneNumber().trim();
        String routingContext = normalizeRoutingContext(req.getRoutingContext());
        LambdaQueryWrapper<OutboundNumberEntity> check = new LambdaQueryWrapper<OutboundNumberEntity>()
                .isNull(OutboundNumberEntity::getDeletedAt)
                .eq(OutboundNumberEntity::getRoutingContext, routingContext)
                .eq(OutboundNumberEntity::getPhoneNumber, phone);
        if (outboundMapper.selectCount(check) > 0) {
            throw new IllegalArgumentException("外呼号码已存在: " + phone);
        }

        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        OutboundNumberEntity entity = OutboundNumberEntity.builder()
                .id(id)
                .routingContext(routingContext)
                .phoneNumber(phone)
                .poolCode(req.getPoolCode() == null ? "default" : req.getPoolCode().trim())
                .status("AVAILABLE")
                .maxConcurrent(req.getMaxConcurrent() == null ? 1 : req.getMaxConcurrent())
                .createdAt(now)
                .updatedAt(now)
                .build();

        outboundMapper.insert(entity);
        log.info("[TelephonyResourceService] 录入外呼号码成功: id={}, phone={}", id, phone);
        return id;
    }

    /**
     * 查询外呼主叫号码池列表
     *
     * @param poolCode 号码池代码 (可选)
     * @return 外呼号码视图列表
     */
    public List<OutboundNumberVO> listOutboundNumbers(String poolCode) {
        LambdaQueryWrapper<OutboundNumberEntity> wrapper = new LambdaQueryWrapper<OutboundNumberEntity>()
                .eq(poolCode != null && !poolCode.isBlank(), OutboundNumberEntity::getPoolCode, poolCode)
                .orderByAsc(OutboundNumberEntity::getPhoneNumber);

        List<OutboundNumberEntity> list = outboundMapper.selectList(wrapper);
        return list.stream().map(o -> OutboundNumberVO.builder()
                .id(o.getId())
                .phoneNumber(o.getPhoneNumber())
                .routingContext(o.getRoutingContext())
                .poolCode(o.getPoolCode())
                .status(o.getStatus())
                .maxConcurrent(o.getMaxConcurrent())
                .createdAt(o.getCreatedAt())
                .build()
        ).toList();
    }

    /**
     * 查询通信节点集群健康与负载列表
     *
     * @return 节点运行状态视图列表
     */
    public List<TelephonyNodeVO> listTelephonyNodes() {
        List<TelephonyNodeEntity> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<TelephonyNodeEntity>()
                        .orderByAsc(TelephonyNodeEntity::getNodeId));

        return nodes.stream().map(n -> TelephonyNodeVO.builder()
                .id(n.getId())
                .nodeId(n.getNodeId())
                .clusterId(n.getClusterId())
                .host(n.getHost())
                .zone(n.getZone())
                .status(n.getStatus())
                .maxChannels(n.getMaxChannels())
                .activeChannels(n.getActiveChannels())
                .lastHeartbeatAt(n.getLastHeartbeatAt())
                .build()
        ).toList();
    }

    /**
     * 规范并校验 FreeSWITCH 拨号计划上下文。
     *
     * @param routingContext 管理端提交的上下文
     * @return 去除首尾空白后的合法上下文
     * @throws IllegalArgumentException 上下文缺失或包含不受支持字符
     */
    private String normalizeRoutingContext(String routingContext) {
        String normalized = routingContext == null ? "" : routingContext.trim();
        if (!normalized.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("拨号上下文不合法");
        }
        return normalized;
    }
}
