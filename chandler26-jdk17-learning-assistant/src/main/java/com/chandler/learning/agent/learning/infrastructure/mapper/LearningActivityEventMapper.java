package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.entity.LearningActivityEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 学习活动原始事件数据访问接口。 */
@Mapper
public interface LearningActivityEventMapper extends BaseMapper<LearningActivityEvent> {

    /** 幂等写入活动事件，重复请求不产生重复记录。 */
    int insertIgnore(LearningActivityEvent event);

    /** 原子领取一批待投影事件。 */
    int claimPendingBatch(@Param("claimToken") String claimToken, @Param("limit") int limit);

    /** 查询指定领取令牌的事件。 */
    List<LearningActivityEvent> selectByClaimToken(@Param("claimToken") String claimToken);

    /** 将一批事件标记为投影成功。 */
    int markSucceededByClaimToken(@Param("claimToken") String claimToken);

    /** 将超时未完成的事件恢复为待处理。 */
    int resetStaleProcessing(@Param("cutoffTime") LocalDateTime cutoffTime);
}
