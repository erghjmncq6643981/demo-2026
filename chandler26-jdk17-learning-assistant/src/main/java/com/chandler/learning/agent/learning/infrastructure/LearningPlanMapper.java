package com.chandler.learning.agent.learning.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.learning.domain.LearningPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface LearningPlanMapper extends BaseMapper<LearningPlan> {

    /** 原子领取计划级场景生成租约。 */
    int claimGenerationLock(@Param("planId") Long planId,
                            @Param("lockToken") String lockToken,
                            @Param("now") LocalDateTime now,
                            @Param("lockUntil") LocalDateTime lockUntil);

    /** 延长当前调用持有的场景生成租约。 */
    int renewGenerationLock(@Param("planId") Long planId,
                            @Param("lockToken") String lockToken,
                            @Param("lockUntil") LocalDateTime lockUntil);

    /** 仅允许租约持有者释放计划级场景生成租约。 */
    int releaseGenerationLock(@Param("planId") Long planId,
                              @Param("lockToken") String lockToken);
}
