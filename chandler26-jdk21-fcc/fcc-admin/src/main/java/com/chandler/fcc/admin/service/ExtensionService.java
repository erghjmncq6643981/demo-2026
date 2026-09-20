package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.client.SidecarAdminClient;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointBindingEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentEndpointBindingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.ExtensionCreateReq;
import com.chandler.fcc.admin.model.dto.ExtensionQueryReq;
import com.chandler.fcc.admin.model.vo.ExtensionVO;
import com.chandler.fcc.common.dto.admin.SidecarResponse;
import com.chandler.fcc.common.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通信分机与 FreeSWITCH Sidecar 双向同步业务服务
 * <p>
 * 负责分机录入落库、同步调用 Go Sidecar HTTP 接口下发至 FreeSWITCH directory 并 reloadxml，
 * 以及结合 Redis 获取实时注册态与坐席绑定状态。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtensionService {

    private static final String EXTENSION_PRESENCE_PREFIX = "fcc:extension:presence:";

    private final ExtensionMapper extensionMapper;
    private final AgentEndpointBindingMapper bindingMapper;
    private final AgentMapper agentMapper;
    private final SidecarAdminClient sidecarAdminClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final SipCredentialCipher credentialCipher;

    /**
     * 创建并同步分机至 FreeSWITCH
     *
     * @param req 分机创建入参
     * @return 分机雪花主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createExtension(ExtensionCreateReq req) {
        String ext = req.getExtension().trim();

        // 1. 查重
        LambdaQueryWrapper<ExtensionEntity> checkWrapper = new LambdaQueryWrapper<ExtensionEntity>()
                .eq(ExtensionEntity::getExtension, ext);
        if (extensionMapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException("分机号已存在: " + ext);
        }

        byte[] encryptedSecret = credentialCipher.encrypt(req.getPassword().trim());
        // 2. 同步调用 Go Sidecar HTTP 接口下发 FreeSWITCH XML 目录配置
        SidecarResponse<?> resp = sidecarAdminClient.createExtension(ext, req.getPassword().trim());
        boolean sidecarOk = resp != null && (resp.getCode() == 200 || resp.getCode() == 0);
        if (!sidecarOk) {
            log.warn("[ExtensionService] Go Sidecar 创建分机返回失败或超时，仍写入本地数据库: ext={}", ext);
        } else {
            log.info("[ExtensionService] Go Sidecar 同步创建分机成功: ext={}", ext);
        }

        // 3. 落库 fcc_extension
        Long id = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        ExtensionEntity entity = ExtensionEntity.builder()
                .id(id)
                .extension(ext)
                .endpointType(req.getEndpointType() == null ? "SIP" : req.getEndpointType().trim())
                .credentialSecret(encryptedSecret)
                .status(sidecarOk ? "ENABLED" : "PROVISIONING_FAILED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        extensionMapper.insert(entity);
        log.info("[ExtensionService] 成功创建并入库分机: id={}, ext={}", id, ext);
        return id;
    }

    /**
     * 删除分机并从 FreeSWITCH 移除
     *
     * @param id 分机主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteExtension(Long id) {
        ExtensionEntity entity = extensionMapper.selectById(id);
        if (entity == null) {
            return;
        }

        String ext = entity.getExtension();
        // 1. 调用 Sidecar 移除分机
        try {
            sidecarAdminClient.deleteExtension(ext);
        } catch (Exception e) {
            log.warn("[ExtensionService] Go Sidecar 移除分机异常: ext={}, error={}", ext, e.getMessage());
        }

        // 2. 软删除本地记录
        entity.setStatus("DISABLED");
        entity.setDeletedAt(LocalDateTime.now());
        extensionMapper.updateById(entity);

        // 3. 清理 Redis 在线状态
        try {
            if (stringRedisTemplate != null) {
                stringRedisTemplate.delete(EXTENSION_PRESENCE_PREFIX + ext);
            }
        } catch (Exception e) {
            log.warn("[ExtensionService] 清理 Redis 分机注册态异常: ext={}, error={}", ext, e.getMessage());
        }

        log.info("[ExtensionService] 成功删除分机: id={}, ext={}", id, ext);
    }

    /**
     * 分页查询分机列表 (集成 Redis 在线态与坐席绑定关系)
     *
     * @param req 分页过滤入参
     * @return 分机视图对象分页容器
     */
    public PageResult<ExtensionVO> queryExtensions(ExtensionQueryReq req) {
        Page<ExtensionEntity> page = new Page<>(req.getPageNum(), req.getPageSize());
        LambdaQueryWrapper<ExtensionEntity> wrapper = new LambdaQueryWrapper<ExtensionEntity>()
                .isNull(ExtensionEntity::getDeletedAt)
                .like(req.getExtension() != null && !req.getExtension().isBlank(), ExtensionEntity::getExtension, req.getExtension())
                .eq(req.getStatus() != null && !req.getStatus().isBlank(), ExtensionEntity::getStatus, req.getStatus())
                .orderByAsc(ExtensionEntity::getExtension);

        Page<ExtensionEntity> entityPage = extensionMapper.selectPage(page, wrapper);

        List<ExtensionVO> voList = entityPage.getRecords().stream()
                .map(this::buildExtensionVO)
                .toList();

        return PageResult.<ExtensionVO>builder()
                .pageNum(entityPage.getCurrent())
                .pageSize(entityPage.getSize())
                .total(entityPage.getTotal())
                .list(voList)
                .build();
    }

    /**
     * 获取单个分机详情
     *
     * @param extension 分机号
     * @return 分机视图对象 VO
     */
    public ExtensionVO getByExtension(String extension) {
        LambdaQueryWrapper<ExtensionEntity> wrapper = new LambdaQueryWrapper<ExtensionEntity>()
                .isNull(ExtensionEntity::getDeletedAt)
                .eq(ExtensionEntity::getExtension, extension.trim());
        ExtensionEntity entity = extensionMapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return buildExtensionVO(entity);
    }

    /**
     * 组装分机视图对象，填充 Redis 在线态与绑定坐席
     */
    private ExtensionVO buildExtensionVO(ExtensionEntity entity) {
        String ext = entity.getExtension();

        // 1. 查询 Redis 在线状态
        String onlineStatus = "OFFLINE";
        try {
            if (stringRedisTemplate != null) {
                String val = stringRedisTemplate.opsForValue().get(EXTENSION_PRESENCE_PREFIX + ext);
                if ("ONLINE".equalsIgnoreCase(val) || "REGISTERED".equalsIgnoreCase(val)) {
                    onlineStatus = "ONLINE";
                }
            }
        } catch (Exception e) {
            log.debug("[ExtensionService] Redis 获取分机注册态失败: ext={}", ext);
        }

        // 2. 查询绑定的坐席信息 (优先从 fcc_extension 单表直读，0 延迟无连表损耗)
        String boundAgentName = entity.getAgentName();
        String boundWorkNo = entity.getAgentWorkNo();

        if (boundWorkNo == null || boundWorkNo.isBlank()) {
            List<AgentEndpointBindingEntity> bindings = bindingMapper.selectList(
                    new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                            .eq(AgentEndpointBindingEntity::getEndpointValue, ext)
                            .eq(AgentEndpointBindingEntity::getStatus, "ENABLED")
                            .orderByAsc(AgentEndpointBindingEntity::getPriority));

            if (!bindings.isEmpty()) {
                AgentEndpointBindingEntity binding = bindings.getFirst();
                AgentEntity agent = agentMapper.selectById(binding.getAgentId());
                if (agent != null) {
                    boundAgentName = agent.getAgentName();
                    boundWorkNo = agent.getWorkNo();
                }
            }
        }

        return ExtensionVO.builder()
                .id(entity.getId())
                .extension(entity.getExtension())
                .endpointType(entity.getEndpointType())
                .status(entity.getStatus())
                .onlineStatus(onlineStatus)
                .boundAgentName(boundAgentName)
                .boundAgentWorkNo(boundWorkNo)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
