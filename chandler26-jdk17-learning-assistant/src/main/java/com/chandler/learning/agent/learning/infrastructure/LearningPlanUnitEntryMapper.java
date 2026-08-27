package com.chandler.learning.agent.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitEntryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface LearningPlanUnitEntryMapper extends BaseMapper<LearningPlanUnitEntry> {

    int insertBatch(@Param("list") List<LearningPlanUnitEntry> list);

    int updateBatch(@Param("list") List<LearningPlanUnitEntry> list);

    List<LearningPlanUnitEntryItem> selectEntriesWithProgress(
            @Param("planId") Long planId,
            @Param("unitIds") Collection<Long> unitIds);
}
