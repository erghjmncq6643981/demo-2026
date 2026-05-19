package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LearningUserMapper extends BaseMapper<LearningUser> {
}
