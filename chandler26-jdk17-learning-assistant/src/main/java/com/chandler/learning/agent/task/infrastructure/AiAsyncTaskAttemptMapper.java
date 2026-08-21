package com.chandler.learning.agent.task.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.task.domain.AiAsyncTaskAttempt;
import org.apache.ibatis.annotations.Mapper;

/** AI 任务步骤执行尝试数据访问。 */
@Mapper
public interface AiAsyncTaskAttemptMapper extends BaseMapper<AiAsyncTaskAttempt> {
}
