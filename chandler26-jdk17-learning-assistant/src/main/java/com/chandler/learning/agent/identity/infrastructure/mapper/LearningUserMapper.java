package com.chandler.learning.agent.identity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningUserMapper 类。
 */
@Mapper
public interface LearningUserMapper extends BaseMapper<LearningUser> {
}
