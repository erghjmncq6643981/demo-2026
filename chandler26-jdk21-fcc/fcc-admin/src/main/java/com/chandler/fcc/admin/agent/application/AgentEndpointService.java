package com.chandler.fcc.admin.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.agent.application.model.AgentEndpointOverview;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointBindingEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointSelectionAuditEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentEndpointBindingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentEndpointSelectionAuditMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.ExtensionMapper;
import com.chandler.fcc.common.enums.AnswerEndpointType;
import com.chandler.fcc.common.util.IdUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理坐席终端清单与唯一当前接听终端。
 *
 * <p>物理 SIP 绑定只能由运行面的 {@code 0000} 拨号流程建立。本服务只允许在已经验证且
 * 启用的绑定中切换当前接听终端。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentEndpointService {

    private static final String ENABLED = "ENABLED";

    private final AgentMapper agentMapper;
    private final AgentEndpointBindingMapper bindingMapper;
    private final AgentEndpointSelectionAuditMapper auditMapper;
    private final ExtensionMapper extensionMapper;

    /**
     * 查询坐席完整终端配置。
     *
     * @param workNo 坐席工号
     * @return 终端配置
     * @throws IllegalArgumentException 坐席不存在或当前终端事实不唯一
     */
    public AgentEndpointOverview get(String workNo) {
        AgentEntity agent = findAgent(workNo);
        List<AgentEndpointBindingEntity> bindings = enabledBindings(agent.getId());
        List<AgentEndpointBindingEntity> activeBindings = bindings.stream()
            .filter(binding -> Boolean.TRUE.equals(binding.getActive()))
            .toList();
        if (activeBindings.size() != 1) {
            throw new IllegalArgumentException("坐席当前接听终端缺失或不唯一: workNo=" + workNo);
        }

        AgentEndpointBindingEntity active = activeBindings.getFirst();
        String webrtc = bindings.stream()
            .filter(binding -> AnswerEndpointType.WEBRTC.name().equals(binding.getEndpointType()))
            .map(AgentEndpointBindingEntity::getEndpointValue)
            .findFirst()
            .orElse(null);
        List<String> sipExtensions = bindings.stream()
            .filter(binding -> AnswerEndpointType.SIP.name().equals(binding.getEndpointType()))
            .map(AgentEndpointBindingEntity::getEndpointValue)
            .filter(value -> value != null && !value.isBlank())
            .toList();

        return AgentEndpointOverview.builder()
            .workNo(agent.getWorkNo())
            .agentName(agent.getAgentName())
            .activeEndpointType(active.getEndpointType())
            .activeEndpointValue(active.getEndpointValue())
            .webrtcWorkNo(webrtc)
            .sipExtension(sipExtensions.isEmpty() ? null : sipExtensions.getFirst())
            .mobilePhone(agent.getPhoneNumber())
            .availableSipExtensions(sipExtensions)
            .build();
    }

    /**
     * 在事务中切换坐席当前接听终端。
     *
     * @param workNo 坐席工号
     * @param endpointType 目标终端类型
     * @param endpointValue 目标终端值；WebRTC 可为空
     * @param actor 操作者登录标识
     * @return 切换后的终端配置
     * @throws ResponseStatusException 手机能力未实现、通话状态阻断或绑定不可用
     */
    @Transactional(rollbackFor = Exception.class)
    public AgentEndpointOverview switchEndpoint(
        String workNo,
        String endpointType,
        String endpointValue,
        String actor
    ) {
        AnswerEndpointType type = AnswerEndpointType.from(endpointType);
        if (!type.isRuntimeSupported()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "手机接听尚未实现，不能设为当前终端");
        }

        String normalizedWorkNo = normalizeWorkNo(workNo);
        Long agentId = bindingMapper.lockAgentId(normalizedWorkNo);
        if (agentId == null) {
            throw new IllegalArgumentException("坐席不存在或已停用: workNo=" + normalizedWorkNo);
        }
        if (bindingMapper.countSwitchBlockingState(agentId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "通话中或话后整理中不能切换接听终端");
        }

        String targetValue = type == AnswerEndpointType.WEBRTC
            ? normalizedWorkNo
            : normalizeEndpointValue(endpointValue);
        List<AgentEndpointBindingEntity> matches = bindingMapper.selectList(
            new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                .eq(AgentEndpointBindingEntity::getAgentId, agentId)
                .eq(AgentEndpointBindingEntity::getEndpointType, type.name())
                .eq(AgentEndpointBindingEntity::getEndpointValue, targetValue)
                .eq(AgentEndpointBindingEntity::getStatus, ENABLED)
        );
        if (matches.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标终端尚未完成有效绑定");
        }
        validateExtension(type, targetValue);

        AgentEndpointBindingEntity target = matches.getFirst();
        AgentEndpointBindingEntity previous = currentBinding(agentId);
        if (previous != null && previous.getId().equals(target.getId())) {
            return get(normalizedWorkNo);
        }
        bindingMapper.deactivateByAgentId(agentId);
        if (bindingMapper.activate(agentId, target.getId()) != 1) {
            throw new IllegalStateException("目标终端在切换过程中失效");
        }
        auditMapper.insert(AgentEndpointSelectionAuditEntity.builder()
            .id(IdUtil.nextId())
            .agentId(agentId)
            .actor(actor)
            .oldBindingId(previous == null ? null : previous.getId())
            .newBindingId(target.getId())
            .result("SUCCESS")
            .createdAt(LocalDateTime.now())
            .build());
        log.info(
            "[坐席终端] 切换成功 actor={}, workNo={}, endpointType={}, endpointValue={}",
            actor,
            normalizedWorkNo,
            type.name(),
            targetValue
        );
        return get(normalizedWorkNo);
    }

    /**
     * 读取坐席当前接听绑定。
     *
     * @param agentId 坐席主键
     * @return 当前绑定；不存在时为空
     */
    private AgentEndpointBindingEntity currentBinding(Long agentId) {
        List<AgentEndpointBindingEntity> current = bindingMapper.selectList(
            new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                .eq(AgentEndpointBindingEntity::getAgentId, agentId)
                .eq(AgentEndpointBindingEntity::getStatus, ENABLED)
                .eq(AgentEndpointBindingEntity::getActive, true)
        );
        if (current.size() > 1) {
            throw new IllegalStateException("坐席存在多个当前接听终端");
        }
        return current.isEmpty() ? null : current.getFirst();
    }

    /**
     * 查询坐席全部有效绑定。
     *
     * @param agentId 坐席主键
     * @return 有效绑定
     */
    private List<AgentEndpointBindingEntity> enabledBindings(Long agentId) {
        return bindingMapper.selectList(
            new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                .eq(AgentEndpointBindingEntity::getAgentId, agentId)
                .eq(AgentEndpointBindingEntity::getStatus, ENABLED)
                .orderByAsc(AgentEndpointBindingEntity::getEndpointType)
                .orderByAsc(AgentEndpointBindingEntity::getId)
        );
    }

    /**
     * 查询启用坐席。
     *
     * @param workNo 坐席工号
     * @return 坐席实体
     */
    private AgentEntity findAgent(String workNo) {
        String normalized = normalizeWorkNo(workNo);
        AgentEntity agent = agentMapper.selectOne(
            new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkNo, normalized)
                .eq(AgentEntity::getStatus, ENABLED)
                .isNull(AgentEntity::getDeletedAt)
        );
        if (agent == null) {
            throw new IllegalArgumentException("坐席不存在或已停用: workNo=" + normalized);
        }
        return agent;
    }

    /**
     * 校验目标分机资源仍然有效且类型一致。
     *
     * @param type 终端类型
     * @param value 终端值
     */
    private void validateExtension(AnswerEndpointType type, String value) {
        long count = extensionMapper.selectCount(
            new LambdaQueryWrapper<ExtensionEntity>()
                .eq(ExtensionEntity::getExtension, value)
                .eq(ExtensionEntity::getEndpointType, type.name())
                .eq(ExtensionEntity::getStatus, ENABLED)
                .isNull(ExtensionEntity::getDeletedAt)
        );
        if (count != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标分机资源未启用或类型不匹配");
        }
    }

    /**
     * 归一化坐席工号。
     *
     * @param workNo 坐席工号
     * @return 去除首尾空白后的工号
     */
    private String normalizeWorkNo(String workNo) {
        if (workNo == null || workNo.isBlank()) {
            throw new IllegalArgumentException("坐席工号不能为空");
        }
        return workNo.trim();
    }

    /**
     * 归一化目标终端值。
     *
     * @param endpointValue 终端值
     * @return 去除首尾空白后的终端值
     */
    private String normalizeEndpointValue(String endpointValue) {
        if (endpointValue == null || endpointValue.isBlank()) {
            throw new IllegalArgumentException("目标终端不能为空");
        }
        return endpointValue.trim();
    }
}
