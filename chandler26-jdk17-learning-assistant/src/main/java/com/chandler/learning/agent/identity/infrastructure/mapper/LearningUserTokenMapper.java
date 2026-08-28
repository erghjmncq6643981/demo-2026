package com.chandler.learning.agent.identity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.entity.LearningUserToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * LearningUserTokenMapper 类。
 */
@Mapper
public interface LearningUserTokenMapper extends BaseMapper<LearningUserToken> {
}
