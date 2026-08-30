package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复习数据访问接口。
 */
@Mapper
public interface LearningReviewRecordMapper extends BaseMapper<LearningReviewRecord> {
}
