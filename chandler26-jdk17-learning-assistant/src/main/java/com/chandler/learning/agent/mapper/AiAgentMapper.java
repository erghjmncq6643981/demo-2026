package com.chandler.learning.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.AiAgent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {
}
