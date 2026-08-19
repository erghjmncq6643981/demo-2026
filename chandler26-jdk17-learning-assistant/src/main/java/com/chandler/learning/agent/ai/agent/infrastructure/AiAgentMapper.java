package com.chandler.learning.agent.ai.agent.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.agent.domain.AiAgent;
import org.apache.ibatis.annotations.Mapper;

/**
 * AiAgentMapper 类。
 */
@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {
}
