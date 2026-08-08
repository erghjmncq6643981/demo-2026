package com.chandler.learning.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AiChatMessageMapper 类。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {
}
