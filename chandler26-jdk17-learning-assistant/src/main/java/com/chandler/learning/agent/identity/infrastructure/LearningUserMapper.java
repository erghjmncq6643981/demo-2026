package com.chandler.learning.agent.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.LearningUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningUserMapper 类。
 */
@Mapper
public interface LearningUserMapper extends BaseMapper<LearningUser> {
}
