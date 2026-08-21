package com.chandler.learning.agent.task.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.task.domain.AiAsyncTaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** AI 任务步骤数据访问。 */
@Mapper
public interface AiAsyncTaskStepMapper extends BaseMapper<AiAsyncTaskStep> {

    int claim(@Param("stepId") Long stepId, @Param("leaseToken") String leaseToken,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    int recoverExpired(@Param("now") LocalDateTime now);
}
