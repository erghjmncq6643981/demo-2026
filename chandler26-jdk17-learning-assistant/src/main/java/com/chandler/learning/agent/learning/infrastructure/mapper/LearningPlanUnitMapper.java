package com.chandler.learning.agent.learning.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.bo.LearningPlanUnitItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface LearningPlanUnitMapper extends BaseMapper<LearningPlanUnit> {

    /**
     * 查询学习计划内当前最大的单元序号（包括已软删除的记录，保证新单元序号在唯一索引约束下严格递增）。
     *
     * @param planId 学习计划 ID
     * @return 最大序号，若无记录返回 null
     */
    Integer selectMaxUnitNoIncludingDeleted(@Param("planId") Long planId);

    /**
     * 联表查询单元及其生效中的场景材料。
     *
     * @param planId 学习计划 ID
     * @param unitIds 指定单元 ID 列表，传 null 或空时查询计划下全部单元
     * @return 单元和材料聚合列表
     */
    List<LearningPlanUnitItem> selectUnitsWithMaterial(
            @Param("planId") Long planId,
            @Param("unitIds") Collection<Long> unitIds);
}
