package com.chandler.learning.agent.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.LearningSceneMaterialNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 场景材料笔记数据访问。
 */
@Mapper
public interface LearningSceneMaterialNoteMapper extends BaseMapper<LearningSceneMaterialNote> {
}
