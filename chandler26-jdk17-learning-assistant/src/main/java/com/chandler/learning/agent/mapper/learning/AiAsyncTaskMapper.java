package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.AiAsyncTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 异步任务数据访问。
 */
@Mapper
public interface AiAsyncTaskMapper extends BaseMapper<AiAsyncTask> {
}
