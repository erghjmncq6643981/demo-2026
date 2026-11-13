package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 知识反刍微环境探针数据访问接口。
 */
@Mapper
public interface LearningRuminationProbeMapper extends BaseMapper<LearningRuminationProbe> {

    /** 加行级排他锁读取未删除的微环境探针，确保并发作答请求串行判分。 */
    LearningRuminationProbe selectByIdForUpdate(@Param("probeId") Long probeId, @Param("userId") Long userId);

    /** 条件原子落库首份作答结果，仅当 answered_time 为空时更新生效。 */
    int markAnsweredAtomically(@Param("probeId") Long probeId,
                               @Param("userId") Long userId,
                               @Param("selectedAnswer") String selectedAnswer,
                               @Param("isCorrect") Boolean isCorrect,
                               @Param("score") Integer score,
                               @Param("illuminated") Boolean illuminated,
                               @Param("answeredTime") LocalDateTime answeredTime);
}
