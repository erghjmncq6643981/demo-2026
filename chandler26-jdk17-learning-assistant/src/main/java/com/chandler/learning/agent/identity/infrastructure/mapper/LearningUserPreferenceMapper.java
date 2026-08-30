package com.chandler.learning.agent.identity.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.entity.LearningUserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户账户数据访问接口。
 */
@Mapper
public interface LearningUserPreferenceMapper extends BaseMapper<LearningUserPreference> {

    /** 按用户与偏好键唯一约束批量新增或更新。 */
    int upsertBatch(@Param("list") List<LearningUserPreference> list);
}
