package com.chandler.learning.agent.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.LearningUserPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningUserPreferenceMapper 类。
 */
@Mapper
public interface LearningUserPreferenceMapper extends BaseMapper<LearningUserPreference> {
}
