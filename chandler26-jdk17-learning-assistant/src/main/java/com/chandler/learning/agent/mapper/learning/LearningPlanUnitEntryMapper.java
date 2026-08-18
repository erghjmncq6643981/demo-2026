package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnitEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LearningPlanUnitEntryMapper extends BaseMapper<LearningPlanUnitEntry> {

    int insertBatch(@Param("list") List<LearningPlanUnitEntry> list);
}
