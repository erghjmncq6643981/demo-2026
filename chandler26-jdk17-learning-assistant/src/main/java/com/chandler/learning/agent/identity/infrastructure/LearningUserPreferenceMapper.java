package com.chandler.learning.agent.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.identity.domain.LearningUserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LearningUserPreferenceMapper 类。
 */
@Mapper
public interface LearningUserPreferenceMapper extends BaseMapper<LearningUserPreference> {

    /** 按用户与偏好键唯一约束批量新增或更新。 */
    int upsertBatch(@Param("list") List<LearningUserPreference> list);
}
