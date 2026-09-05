package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.bo.LearningAssessmentPassBO;
import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Collection;

/**
 * 复习数据访问接口。
 */
@Mapper
public interface LearningReviewRecordMapper extends BaseMapper<LearningReviewRecord> {

    /**
     * 查询指定场景单元中指定生词本词条已通过的检查类型。
     */
    List<String> selectPassedAssessmentTypes(
            @Param("unitId") Long unitId,
            @Param("entryId") Long entryId);

    /**
     * 批量查询场景单元内多个词条已经通过的评测类型。
     * 仅返回正确记录，调用方可按 entryId 分组后计算场景完成度。
     */
    List<LearningAssessmentPassBO> selectPassedAssessmentTypesBatch(
            @Param("unitId") Long unitId,
            @Param("entryIds") Collection<Long> entryIds);
}
