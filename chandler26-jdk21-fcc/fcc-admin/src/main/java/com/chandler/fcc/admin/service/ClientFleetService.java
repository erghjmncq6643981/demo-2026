package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ClientHardwareRecordEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ClientVersionReleaseEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ClientHardwareRecordMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ClientVersionReleaseMapper;
import com.chandler.fcc.admin.model.dto.ClientVersionReleaseReq;
import com.chandler.fcc.admin.model.vo.ClientHardwareRecordVO;
import com.chandler.fcc.admin.model.vo.ClientVersionReleaseVO;
import com.chandler.fcc.common.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 坐席 PC 客户端机队治理业务服务
 * <p>
 * 统一管理客户端版本发布更新、强更策略控制与坐席硬件指纹安全审计。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientFleetService {

    private final ClientVersionReleaseMapper versionMapper;
    private final ClientHardwareRecordMapper hardwareMapper;

    /**
     * 发布客户端新版本
     *
     * @param req      版本发布入参
     * @param operator 发布操作人
     * @return 版本记录主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publishVersion(ClientVersionReleaseReq req, String operator) {
        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();

        ClientVersionReleaseEntity entity = ClientVersionReleaseEntity.builder()
                .id(id)
                .tenantId(0L)
                .version(req.getVersion().trim())
                .platform(req.getPlatform() == null ? "WINDOWS" : req.getPlatform().trim().toUpperCase())
                .downloadUrl(req.getDownloadUrl().trim())
                .fileMd5(req.getFileMd5())
                .forceUpdate(req.getForceUpdate() != null && req.getForceUpdate())
                .status("RELEASED")
                .releaseNotes(req.getReleaseNotes())
                .releasedAt(now)
                .createdBy(operator)
                .createdAt(now)
                .updatedAt(now)
                .build();

        versionMapper.insert(entity);
        log.info("[ClientFleetService] 成功发布客户端版本: platform={}, version={}", req.getPlatform(), req.getVersion());
        return id;
    }

    /**
     * 查询指定平台的版本发布历史列表
     *
     * @param platform 操作系统平台 (WINDOWS, MAC, LINUX, WEB)
     * @return 版本发布展示列表
     */
    public List<ClientVersionReleaseVO> listVersions(String platform) {
        LambdaQueryWrapper<ClientVersionReleaseEntity> wrapper = new LambdaQueryWrapper<ClientVersionReleaseEntity>()
                .eq(platform != null && !platform.isBlank(), ClientVersionReleaseEntity::getPlatform, platform)
                .orderByDesc(ClientVersionReleaseEntity::getReleasedAt);

        List<ClientVersionReleaseEntity> list = versionMapper.selectList(wrapper);
        return list.stream().map(v -> ClientVersionReleaseVO.builder()
                .id(v.getId())
                .version(v.getVersion())
                .platform(v.getPlatform())
                .downloadUrl(v.getDownloadUrl())
                .fileMd5(v.getFileMd5())
                .forceUpdate(v.getForceUpdate())
                .status(v.getStatus())
                .releaseNotes(v.getReleaseNotes())
                .releasedAt(v.getReleasedAt())
                .createdBy(v.getCreatedBy())
                .build()
        ).toList();
    }

    /**
     * 录入坐席客户端硬件指纹安全审计记录
     *
     * @param agentId       坐席 ID
     * @param workNum       工号
     * @param clientVersion 客户端版本号
     * @param macAddr       MAC 地址
     * @param os            操作系统信息
     * @param ipAddr        网络 IP
     * @return 硬件审计主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long recordHardware(Long agentId, String workNum, String clientVersion,
                               String macAddr, String os, String ipAddr) {
        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();

        ClientHardwareRecordEntity record = ClientHardwareRecordEntity.builder()
                .id(id)
                .tenantId(0L)
                .agentId(agentId)
                .workNum(workNum)
                .clientVersion(clientVersion)
                .macAddr(macAddr)
                .os(os)
                .ipAddr(ipAddr)
                .loginTime(now)
                .createdAt(now)
                .build();

        hardwareMapper.insert(record);
        log.info("[ClientFleetService] 记录坐席硬件指纹: agentId={}, workNum={}, mac={}", agentId, workNum, macAddr);
        return id;
    }

    /**
     * 查询坐席硬件登录审计轨迹
     *
     * @param agentId 坐席 ID (可选)
     * @return 硬件审计记录列表
     */
    public List<ClientHardwareRecordVO> listHardwareAudit(Long agentId) {
        LambdaQueryWrapper<ClientHardwareRecordEntity> wrapper = new LambdaQueryWrapper<ClientHardwareRecordEntity>()
                .eq(agentId != null, ClientHardwareRecordEntity::getAgentId, agentId)
                .orderByDesc(ClientHardwareRecordEntity::getLoginTime);

        List<ClientHardwareRecordEntity> list = hardwareMapper.selectList(wrapper);
        return list.stream().map(h -> ClientHardwareRecordVO.builder()
                .id(h.getId())
                .agentId(h.getAgentId())
                .workNum(h.getWorkNum())
                .clientVersion(h.getClientVersion())
                .macAddr(h.getMacAddr())
                .os(h.getOs())
                .ipAddr(h.getIpAddr())
                .loginTime(h.getLoginTime())
                .build()
        ).toList();
    }
}
