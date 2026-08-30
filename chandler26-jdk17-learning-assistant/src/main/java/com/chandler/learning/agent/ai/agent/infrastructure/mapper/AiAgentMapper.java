package com.chandler.learning.agent.ai.agent.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.agent.domain.entity.AiAgent;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Agent数据访问接口。
 */
@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {
}
