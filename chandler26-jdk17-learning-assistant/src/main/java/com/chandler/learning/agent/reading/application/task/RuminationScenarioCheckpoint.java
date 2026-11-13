package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;

import java.util.List;

/**
 * 知识反刍自定义场景生成阶段 1 产出的星系拓扑可恢复检查点。
 *
 * @param scenarioCode     自定义场景编码
 * @param scenarioName     场景中文名称
 * @param scenarioIcon     场景图标 Emoji
 * @param generationSource 生成来源：ai 默认大模型生成，fallback 本地语义引擎降级
 * @param nodes            待落库的场景星系节点列表
 * @param edges            待落库的场景星系连线列表
 */
public record RuminationScenarioCheckpoint(
        String scenarioCode,
        String scenarioName,
        String scenarioIcon,
        String generationSource,
        List<RuminationNodeResponse> nodes,
        List<RuminationEdgeResponse> edges
) {
}
