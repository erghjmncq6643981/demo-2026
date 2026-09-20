package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.DidNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.OutboundNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.TelephonyNodeEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.TelephonyTrunkEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.OutboundNumberMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.TelephonyNodeMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.TelephonyTrunkMapper;
import com.chandler.fcc.admin.model.dto.DidNumberCreateReq;
import com.chandler.fcc.admin.model.dto.OutboundNumberCreateReq;
import com.chandler.fcc.admin.model.dto.TrunkCreateReq;
import com.chandler.fcc.admin.model.vo.DidNumberVO;
import com.chandler.fcc.admin.model.vo.OutboundNumberVO;
import com.chandler.fcc.admin.model.vo.TelephonyNodeVO;
import com.chandler.fcc.admin.model.vo.TrunkVO;
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
 * 统一管理 SIP 中继网关、呼入 DID 引示号、外呼主叫号池及 FreeSWITCH 通信节点集群状态。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelephonyResourceService {

    private final TelephonyTrunkMapper trunkMapper;
    private final DidNumberMapper didMapper;
    private final OutboundNumberMapper outboundMapper;
    private final TelephonyNodeMapper nodeMapper;

    /**
     * 创建 SIP 通信中继线路
     *
     * @param req 中继创建入参
     * @return 中继雪花主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createTrunk(TrunkCreateReq req) {
        LambdaQueryWrapper<TelephonyTrunkEntity> check = new LambdaQueryWrapper<TelephonyTrunkEntity>()
                .eq(TelephonyTrunkEntity::getTrunkCode, req.getTrunkCode().trim());
        if (trunkMapper.selectCount(check) > 0) {
            throw new IllegalArgumentException("中继编码已存在: " + req.getTrunkCode());
        }

        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        TelephonyTrunkEntity entity = TelephonyTrunkEntity.builder()
                .id(id)
                .trunkCode(req.getTrunkCode().trim())
                .trunkName(req.getTrunkName().trim())
                .carrierCode(req.getCarrierCode())
                .gatewayName(req.getGatewayName().trim())
                .direction(req.getDirection())
                .status("ENABLED")
                .maxConcurrent(req.getMaxConcurrent())
                .configJson(req.getConfigJson())
                .createdAt(now)
                .updatedAt(now)
                .build();

        trunkMapper.insert(entity);
        log.info("[TelephonyResourceService] 创建中继成功: id={}, code={}", id, req.getTrunkCode());
        return id;
    }

    /**
     * 查询全量有效中继线路列表
     *
     * @return 中继视图列表
     */
    public List<TrunkVO> listTrunks() {
        List<TelephonyTrunkEntity> trunks = trunkMapper.selectList(
                new LambdaQueryWrapper<TelephonyTrunkEntity>()
                        .isNull(TelephonyTrunkEntity::getDeletedAt)
                        .orderByAsc(TelephonyTrunkEntity::getId));

        return trunks.stream().map(t -> TrunkVO.builder()
                .id(t.getId())
                .trunkCode(t.getTrunkCode())
                .trunkName(t.getTrunkName())
                .carrierCode(t.getCarrierCode())
                .gatewayName(t.getGatewayName())
                .direction(t.getDirection())
                .status(t.getStatus())
                .maxConcurrent(t.getMaxConcurrent())
                .createdAt(t.getCreatedAt())
                .build()
        ).toList();
    }

    /**
     * 删除中继线路
     *
     * @param id 中继主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTrunk(Long id) {
        TelephonyTrunkEntity trunk = trunkMapper.selectById(id);
        if (trunk != null) {
            trunk.setStatus("DISABLED");
            trunk.setDeletedAt(LocalDateTime.now());
            trunkMapper.updateById(trunk);
            log.info("[TelephonyResourceService] 成功软删除中继: id={}", id);
        }
    }

    /**
     * 录入呼入 DID 引示号
     *
     * @param req DID 创建入参
     * @return DID 主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createDidNumber(DidNumberCreateReq req) {
        String phone = req.getPhoneNumber().trim();
        LambdaQueryWrapper<DidNumberEntity> check = new LambdaQueryWrapper<DidNumberEntity>()
                .isNull(DidNumberEntity::getDeletedAt)
                .eq(DidNumberEntity::getPhoneNumber, phone);
        if (didMapper.selectCount(check) > 0) {
            throw new IllegalArgumentException("DID号码已存在: " + phone);
        }

        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        DidNumberEntity entity = DidNumberEntity.builder()
                .id(id)
                .trunkId(req.getTrunkId())
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
            TelephonyTrunkEntity trunk = d.getTrunkId() != null ? trunkMapper.selectById(d.getTrunkId()) : null;
            return DidNumberVO.builder()
                    .id(d.getId())
                    .phoneNumber(d.getPhoneNumber())
                    .trunkId(d.getTrunkId())
                    .routeKey(d.getRouteKey())
                    .status(d.getStatus())
                    .createdAt(d.getCreatedAt())
                    .build();
        }).toList();
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
        LambdaQueryWrapper<OutboundNumberEntity> check = new LambdaQueryWrapper<OutboundNumberEntity>()
                .eq(OutboundNumberEntity::getPhoneNumber, phone);
        if (outboundMapper.selectCount(check) > 0) {
            throw new IllegalArgumentException("外呼号码已存在: " + phone);
        }

        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        OutboundNumberEntity entity = OutboundNumberEntity.builder()
                .id(id)
                .trunkId(req.getTrunkId())
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
                .trunkId(o.getTrunkId())
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
}
