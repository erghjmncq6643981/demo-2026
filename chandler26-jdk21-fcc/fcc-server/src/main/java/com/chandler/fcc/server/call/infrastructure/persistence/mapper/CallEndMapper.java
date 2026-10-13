package com.chandler.fcc.server.call.infrastructure.persistence.mapper;

import com.chandler.fcc.server.call.domain.CallEndDecision;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallChannelFact;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallCleanupCandidate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 通话结束与数据库残留对账端口；所有写操作在短事务内批量执行。 */
@Mapper
public interface CallEndMapper {
    /**
     * 按主键游标读取过期通话及已结束通话的关联残留。
     * @param after 上页最后主键
     * @param cutoff UTC 过期边界
     * @param limit 每页最大行数
     * @return 不包含大载荷的候选通话
     */
    List<CallCleanupCandidate> candidates(@Param("after") long after,
        @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    /**
     * 批量锁定提交终态所需的当前版本。
     * @param ids 通话主键列表
     * @return 当前通话摘要
     */
    List<CallCleanupCandidate> lockCalls(@Param("ids") List<Long> ids);

    /**
     * 依据真实话道身份查找持久通话，供内存会话释放后的结束/录音事件使用。
     * @param ctrlId 可选控制标识
     * @param channelUuid 实际话道标识
     * @param nodeId 源节点标识
     * @return 唯一持久通话摘要，未匹配时为空
     */
    CallCleanupCandidate resolveChannel(@Param("ctrlId") String ctrlId,
        @Param("channelUuid") String channelUuid, @Param("nodeId") String nodeId);

    /**
     * 保存单 Leg 的真实结束事实，不提前结束仍有其他话道的 Call。
     * @param callId 已解析的通话主键
     * @param channelUuid 单话道标识
     * @param nodeId 实际节点归属
     * @param endedAt 源 UTC 结束时间
     * @param cause 源挂机原因
     * @return 更新 Leg 数量
     */
    int recordLegEnd(@Param("callId") Long callId, @Param("channelUuid") String channelUuid,
        @Param("nodeId") String nodeId, @Param("endedAt") LocalDateTime endedAt, @Param("cause") String cause);

    /**
     * 批量读取实际话道身份及 DESTROY 源事实。
     * @param ids 通话主键列表
     * @return 话道及结束事实，可能含同话道的多条证据
     */
    List<CallChannelFact> channelFacts(@Param("ids") List<Long> ids);

    /**
     * 乐观更新通话终态，已有终态与真实结果不被恢复未知覆盖。
     * @param decisions 经校验的终态决策
     * @return 更新通话数量
     */
    int completeCalls(@Param("decisions") List<CallEndDecision> decisions);

    /**
     * 根据已保存的源事件，或确认缺失的完整快照，结束残留 Leg。
     * @param ids 已结束通话主键
     * @param channelsAbsent 是否允许以快照缺失结束没有源事件的 Leg
     * @return 更新话道数量
     */
    int closeLegs(@Param("ids") List<Long> ids, @Param("channelsAbsent") boolean channelsAbsent);

    /**
     * 结束通话下尚未退出的桥接成员。
     * @param ids 已结束通话主键
     * @return 更新成员数量
     */
    int closeBridgeMembers(@Param("ids") List<Long> ids);

    /**
     * 结束通话下尚未关闭的桥接。
     * @param ids 已结束通话主键
     * @return 更新桥接数量
     */
    int closeBridges(@Param("ids") List<Long> ids);

    /**
     * 中断没有最终结果的步骤，保留已成功或失败步骤。
     * @param ids 已结束通话主键
     * @return 更新步骤数量
     */
    int closeSteps(@Param("ids") List<Long> ids);

    /**
     * 结束残留流程实例并递增版本。
     * @param ids 已结束通话主键
     * @return 更新实例数量
     */
    int closeFlows(@Param("ids") List<Long> ids);

    /**
     * 只释放仍指向原通话的坐席占用，进入话后整理。
     * @param ids 已结束通话主键
     * @return 更新坐席数量
     */
    int releaseAgents(@Param("ids") List<Long> ids);

    /**
     * 将缺少最终录音事件的残留状态转为待对账，不伪造文件完成。
     * @param ids 已结束通话主键
     * @return 更新录音数量
     */
    int markRecordingsUnknown(@Param("ids") List<Long> ids);
}
