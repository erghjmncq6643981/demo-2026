package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.bo.LearningActivityMetricBO;
import com.chandler.learning.agent.learning.domain.entity.LearningActivityDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 学习活动日汇总数据访问接口。 */
@Mapper
public interface LearningActivityDailyMapper extends BaseMapper<LearningActivityDaily> {

    /** 批量累加活动日指标。 */
    int upsertBatch(@Param("list") Collection<LearningActivityDaily> list);

    /** 查询用户日期范围内的日指标。 */
    List<LearningActivityMetricBO> selectMetrics(@Param("userId") Long userId,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);
}
