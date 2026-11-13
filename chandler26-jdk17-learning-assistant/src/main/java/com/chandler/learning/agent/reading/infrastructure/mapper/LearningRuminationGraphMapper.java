package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识反刍星系图谱拓扑与词袋数据访问接口。
 */
@Mapper
public interface LearningRuminationGraphMapper extends BaseMapper<LearningRuminationGraph> {

    /** 加行级排他锁读取指定场景星系图谱状态，串行保护词袋变更与任务结算合并。 */
    LearningRuminationGraph selectByScenarioForUpdate(@Param("userId") Long userId,
                                                      @Param("wordbookId") Long wordbookId,
                                                      @Param("scenarioCode") String scenarioCode);
}
