package com.chandler.learning.agent.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.LearningPlanUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningPlanUnitMapper extends BaseMapper<LearningPlanUnit> {

    /**
     * 查询学习计划内当前最大的单元序号（包括已软删除的记录，保证新单元序号在唯一索引约束下严格递增）。
     *
     * @param planId 学习计划 ID
     * @return 最大序号，若无记录返回 null
     */
    Integer selectMaxUnitNoIncludingDeleted(@Param("planId") Long planId);
}
