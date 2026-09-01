package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.bo.LearningPlanUnitEntryItem;
import com.chandler.learning.agent.learning.domain.bo.LearningPlanUnitWordSummaryItem;
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

    /**
     * 查询日历摘要所需的核心词面，避免加载词卡和题目 JSON。
     */
    List<LearningPlanUnitWordSummaryItem> selectWordSummaries(
            @Param("planId") Long planId,
            @Param("unitIds") Collection<Long> unitIds);

    /**
     * 单条联表查询词汇检查上下文，包含计划、单元、词条、生词本与逐词进度。
     */
    com.chandler.learning.agent.learning.domain.bo.LearningAssessmentContextBO selectAssessmentContext(
            @Param("userId") Long userId,
            @Param("planId") Long planId,
            @Param("unitId") Long unitId,
            @Param("unitEntryId") Long unitEntryId);
}
