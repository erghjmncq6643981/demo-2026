package com.chandler.learning.agent.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.LearningSceneRelatedWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 场景相关词数据访问。 */
@Mapper
public interface LearningSceneRelatedWordMapper extends BaseMapper<LearningSceneRelatedWord> {

    int insertBatch(@Param("list") List<LearningSceneRelatedWord> list);
}
