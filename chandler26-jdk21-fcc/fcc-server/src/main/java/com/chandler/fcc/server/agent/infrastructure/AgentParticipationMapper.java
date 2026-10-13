package com.chandler.fcc.server.agent.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 在 Call 行锁内维护坐席话道参与事实，所有事件来源共享同一身份。 */
@Mapper
public interface AgentParticipationMapper {
    /**
     * 锁定通话，串行化参与创建、替换及结束。
     * @param callId 业务通话标识
     * @return 存在的通话标识
     */
    Long lockCall(@Param("callId") Long callId);

    /**
     * 从已保存话道幂等同步坐席参与，仅信任明确的工号归属。
     * @param legId 话道事实标识
     * @param participationId 新参与记录标识
     * @return 写入数量
     */
    int synchronizeLeg(@Param("legId") Long legId, @Param("participationId") Long participationId);

    /**
     * 根据真实话道结束事实补齐参与终态，不凭 Call 终态伪造结束。
     * @param callIds 原通话标识集合
     * @return 更新数量
     */
    int synchronizeEnded(@Param("callIds") List<Long> callIds);
}
