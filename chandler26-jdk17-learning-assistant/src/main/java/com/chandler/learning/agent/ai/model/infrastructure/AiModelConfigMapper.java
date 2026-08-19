package com.chandler.learning.agent.ai.model.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.model.domain.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AiModelConfigMapper 类。
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
