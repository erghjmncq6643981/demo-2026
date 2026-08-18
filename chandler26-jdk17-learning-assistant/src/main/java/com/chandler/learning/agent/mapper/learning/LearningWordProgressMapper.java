package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningWordProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningWordProgressMapper extends BaseMapper<LearningWordProgress> {

    /** 批量刷新逐词进度中的词卡状态。 */
    int updateCardStatusBatch(@Param("ids") List<Long> ids,
                              @Param("cardStatus") String cardStatus,
                              @Param("updateTime") LocalDateTime updateTime);
}
