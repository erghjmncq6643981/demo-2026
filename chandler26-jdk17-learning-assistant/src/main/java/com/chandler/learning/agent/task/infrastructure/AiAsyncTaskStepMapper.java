package com.chandler.learning.agent.task.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.task.domain.AiAsyncTaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** AI 任务步骤数据访问。 */
@Mapper
public interface AiAsyncTaskStepMapper extends BaseMapper<AiAsyncTaskStep> {

    /** 批量创建父任务的稳定执行步骤。 */
    int insertBatch(@Param("list") List<AiAsyncTaskStep> list);

    int claim(@Param("stepId") Long stepId, @Param("leaseToken") String leaseToken,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 长时间 AI 调用期间续租当前步骤，只有持有令牌的执行器可以更新。 */
    int renew(@Param("stepId") Long stepId, @Param("leaseToken") String leaseToken,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    int recoverExpired(@Param("now") LocalDateTime now);
}
