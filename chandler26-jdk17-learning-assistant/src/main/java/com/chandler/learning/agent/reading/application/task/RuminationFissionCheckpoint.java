package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;

import java.util.List;

/**
 * 知识反刍节点 4D 衍生裂变阶段 1 产出的候选扩展检查点。
 *
 * @param wordbookId       绑定个人单词本 ID
 * @param scenarioCode     绑定主题场景编码
 * @param scenarioName     绑定主题场景名称
 * @param parentNodeId     绑定待裂变父节点标识
 * @param targetWord       待裂变英文核心词
 * @param generationSource 生成来源：ai 默认大模型生成，fallback 本地语义引擎降级
 * @param candidateNodes   候选 4D 衍生节点列表
 * @param candidateEdges   候选 4D 衍生连线列表
 */
public record RuminationFissionCheckpoint(
        Long wordbookId,
        String scenarioCode,
        String scenarioName,
        String parentNodeId,
        String targetWord,
        String generationSource,
        List<RuminationNodeResponse> candidateNodes,
        List<RuminationEdgeResponse> candidateEdges
) {
}
