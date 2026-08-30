package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.reading.domain.entity.LearningArticleStudyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 语境精读数据访问接口。
 */
@Mapper
public interface LearningArticleStudyRecordMapper extends BaseMapper<LearningArticleStudyRecord> {
}
