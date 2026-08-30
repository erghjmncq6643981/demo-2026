package com.chandler.learning.agent.identity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账户数据访问接口。
 */
@Mapper
public interface LearningUserMapper extends BaseMapper<LearningUser> {
}
