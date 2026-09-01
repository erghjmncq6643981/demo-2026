package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}
