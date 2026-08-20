package com.chandler.learning.agent.vocabulary.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.LearningWordbook;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningWordbookMapper 类。
 */
@Mapper
public interface LearningWordbookMapper extends BaseMapper<LearningWordbook> {
}
