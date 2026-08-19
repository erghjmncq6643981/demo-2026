package com.chandler.learning.agent.ai.chat.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.ai.chat.api.ChatSessionResponse;
import com.chandler.learning.agent.ai.chat.api.AdminAiSessionResponse;
import com.chandler.learning.agent.ai.chat.domain.AiChatSession;
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

    /** 管理员按条件查询所有 AI 会话。 */
    List<AdminAiSessionResponse> selectAdminSessionPage(@Param("keyword") String keyword,
                                                        @Param("sceneCode") String sceneCode,
                                                        @Param("provider") String provider,
                                                        @Param("success") Boolean success,
                                                        @Param("offset") int offset,
                                                        @Param("pageSize") int pageSize);

    /** 统计管理员查询条件下的会话数量。 */
    long countAdminSessions(@Param("keyword") String keyword,
                            @Param("sceneCode") String sceneCode,
                            @Param("provider") String provider,
                            @Param("success") Boolean success);

    /** 查询单个会话的管理员摘要。 */
    AdminAiSessionResponse selectAdminSession(@Param("sessionId") Long sessionId);
}
