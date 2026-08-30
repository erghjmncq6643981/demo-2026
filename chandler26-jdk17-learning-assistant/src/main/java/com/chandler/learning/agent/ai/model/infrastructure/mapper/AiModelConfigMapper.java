package com.chandler.learning.agent.ai.model.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.model.domain.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模型数据访问接口。
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
