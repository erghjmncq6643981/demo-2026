package com.chandler.fcc.admin.flow.application.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.flow.infrastructure.FlowStudioMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.DidNumberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.DidNumberMapper;
import com.chandler.fcc.common.enums.FlowTemplateType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;

/** 发布前校验呼入入口及路由目标；不负责发布写入或外部通知。 */
@Component
@RequiredArgsConstructor
public class FlowPublicationPolicy {
    private final DidNumberMapper didNumberMapper;
    private final FlowStudioMapper executionMapper;

    /**
     * 校验呼入流程至少有一个启用的 DID 入口。
     *
     * @param flow 待发布流程主数据
     * @throws ResponseStatusException 呼入流程没有任何可达入口
     */
    public void requireInboundDidBinding(FlowDefinitionEntity flow) {
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
     * 批量校验呼入流程引用的坐席和技能组真实存在且启用。
     *
     * @param flow 待发布流程主数据
     * @param definition 已通过公共协议校验的流程定义
     * @throws ResponseStatusException 任一路由目标不存在或已停用
     */
    public void requireInboundRouteTargets(
        FlowDefinitionEntity flow,
        JsonNode definition
    ) {
        if (!FlowTemplateType.INBOUND.name().equals(flow.getModelType())) {
            return;
        }
        Set<String> agentWorkNos = new HashSet<>();
        Set<String> groupCodes = new HashSet<>();
        collectRouteTarget(definition.path("defaultRoute"), agentWorkNos, groupCodes);
        definition.path("branches").forEach(
            branch -> collectRouteTarget(branch, agentWorkNos, groupCodes)
        );

        Set<String> activeAgents = activeAgentWorkNos(agentWorkNos);
        Set<String> activeGroups = activeGroupCodes(groupCodes);
        agentWorkNos.removeAll(activeAgents);
        groupCodes.removeAll(activeGroups);
        if (!agentWorkNos.isEmpty() || !groupCodes.isEmpty()) {
            String agents = agentWorkNos.isEmpty() ? "" : "坐席 " + String.join(", ", agentWorkNos);
            String groups = groupCodes.isEmpty() ? "" : "技能组 " + String.join(", ", groupCodes);
            String separator = agents.isEmpty() || groups.isEmpty() ? "" : "；";
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "流程引用了不存在或已停用的路由目标：" + agents + separator + groups
            );
        }
    }

    /**
     * 批量查询仍处于启用状态的坐席工号。
     *
     * @param agentWorkNos 流程引用的坐席工号
     * @return 启用的坐席工号集合
     */
    private Set<String> activeAgentWorkNos(Set<String> agentWorkNos) {
        if (agentWorkNos.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(
            executionMapper.activeAgentWorkNos(agentWorkNos.stream().sorted().toList())
        );
    }

    /**
     * 批量查询仍处于启用状态的技能组代码。
     *
     * @param groupCodes 流程引用的技能组代码
     * @return 启用的技能组代码集合
     */
    private Set<String> activeGroupCodes(Set<String> groupCodes) {
        if (groupCodes.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(
            executionMapper.activeGroupCodes(groupCodes.stream().sorted().toList())
        );
    }

    /**
     * 按类型收集一个已校验路由目标。
     *
     * @param route 路由节点
     * @param agentWorkNos 坐席工号集合
     * @param groupCodes 技能组代码集合
     */
    private void collectRouteTarget(
        JsonNode route,
        Set<String> agentWorkNos,
        Set<String> groupCodes
    ) {
        String target = route.path("target").asText();
        if ("AGENT".equals(route.path("targetType").asText())) {
            agentWorkNos.add(target);
        } else {
            groupCodes.add(target);
        }
    }
}
