package com.chandler.learning.agent.ai.agent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.ai.agent.domain.entity.AiAgent;
import com.chandler.learning.agent.ai.agent.infrastructure.mapper.AiAgentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 与模型配置之间的绑定应用服务。
 * <p>
 * 模型域通过该服务维护绑定关系，不能直接访问 Agent Mapper。
 */
@Service
@RequiredArgsConstructor
public class AiAgentBindingService {

    private final AiAgentMapper agentMapper;

    /** 查询绑定指定模型配置的有效 Agent。 */
    public List<AiAgent> listBoundAgents(Long modelConfigId) {
        if (modelConfigId == null) {
            return List.of();
        }
        return agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .eq(AiAgent::getModelConfigId, modelConfigId)
                .eq(AiAgent::getDeleted, false)
                .orderByAsc(AiAgent::getSequence));
    }

    /** 一次查询全部有效绑定并按模型配置分组，避免模型列表产生 N+1 查询。 */
    public Map<Long, List<AiAgent>> groupBoundAgents() {
        Map<Long, List<AiAgent>> result = new LinkedHashMap<>();
        for (AiAgent agent : agentMapper.selectList(new LambdaQueryWrapper<AiAgent>()
                .isNotNull(AiAgent::getModelConfigId)
                .eq(AiAgent::getDeleted, false)
                .orderByAsc(AiAgent::getSequence))) {
            result.computeIfAbsent(agent.getModelConfigId(), ignored -> new ArrayList<>()).add(agent);
        }
        return result;
    }

    /** 模型型号变更后同步 Agent 中用于展示和审计的冗余快照。 */
    public void synchronizeModelSnapshot(Long modelConfigId, String provider, String modelName) {
        agentMapper.update(null, new LambdaUpdateWrapper<AiAgent>()
                .eq(AiAgent::getModelConfigId, modelConfigId)
                .eq(AiAgent::getDeleted, false)
                .set(AiAgent::getModelProvider, provider)
                .set(AiAgent::getModelName, modelName)
                .set(AiAgent::getUpdateTime, LocalDateTime.now()));
    }

    /** 首次维护模型时自动绑定供应商和型号一致的内置 Agent。 */
    public void bindMatchingUnboundAgents(Long modelConfigId, String provider, String modelName) {
        agentMapper.update(null, new LambdaUpdateWrapper<AiAgent>()
                .isNull(AiAgent::getModelConfigId)
                .eq(AiAgent::getModelProvider, provider)
                .eq(AiAgent::getModelName, modelName)
                .eq(AiAgent::getDeleted, false)
                .set(AiAgent::getModelConfigId, modelConfigId)
                .set(AiAgent::getUpdateTime, LocalDateTime.now()));
    }
}
