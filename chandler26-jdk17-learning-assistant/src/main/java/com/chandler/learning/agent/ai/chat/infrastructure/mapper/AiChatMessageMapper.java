package com.chandler.learning.agent.ai.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.chat.domain.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 会话数据访问接口。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /** 查询包含逻辑删除消息在内的下一个会话序号。 */
    int selectNextSequence(@Param("sessionId") Long sessionId);
}
