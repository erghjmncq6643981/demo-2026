package com.chandler.learning.agent.ai.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.prompt.domain.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * AiPromptTemplateMapper 类。
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
