package com.chandler.learning.agent.ai.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.prompt.domain.entity.AiPromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板数据访问接口。
 */
@Mapper
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
