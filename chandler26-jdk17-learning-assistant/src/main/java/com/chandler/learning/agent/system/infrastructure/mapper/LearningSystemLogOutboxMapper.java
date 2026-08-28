package com.chandler.learning.agent.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLogOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 系统日志 Outbox 数据访问。 */
@Mapper
public interface LearningSystemLogOutboxMapper extends BaseMapper<LearningSystemLogOutbox> {

    int claimByIds(@Param("ids") List<Long> ids, @Param("claimToken") String claimToken);

    int claimPendingBatch(@Param("claimToken") String claimToken, @Param("limit") int limit);

    List<LearningSystemLogOutbox> selectByClaimToken(@Param("claimToken") String claimToken);

    int markSucceededByClaimToken(@Param("claimToken") String claimToken);
}
