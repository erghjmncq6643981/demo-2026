package com.chandler.learning.agent.system.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.system.domain.LearningSystemLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningSystemLogMapper 类。
 */
@Mapper
public interface LearningSystemLogMapper extends BaseMapper<LearningSystemLog> {
}
