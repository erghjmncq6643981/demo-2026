package com.chandler.learning.agent.system.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统日志数据访问接口。
 */
@Mapper
public interface LearningSystemLogMapper extends BaseMapper<LearningSystemLog> {

    int insertBatch(List<LearningSystemLog> logs);
}
