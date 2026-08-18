package com.chandler.learning.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.dto.ChatSessionResponse;
import com.chandler.learning.agent.domain.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AiChatSessionMapper 类。
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    /**
     * 一次查询会话列表及消息数量，避免逐会话统计产生 N+1 查询。
     */
    List<ChatSessionResponse> selectSessionSummaries(@Param("userId") Long userId,
                                                     @Param("agentCode") String agentCode,
                                                     @Param("businessType") String businessType,
                                                     @Param("businessId") String businessId,
                                                     @Param("sceneCode") String sceneCode);
}
