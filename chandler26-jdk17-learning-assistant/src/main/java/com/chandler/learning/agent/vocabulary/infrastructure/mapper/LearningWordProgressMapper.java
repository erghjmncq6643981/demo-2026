package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningWordProgressMapper extends BaseMapper<LearningWordProgress> {

    /** 批量新增用户逐词学习进度。 */
    int insertBatch(@Param("list") List<LearningWordProgress> list);

    /** 批量刷新逐词进度中的词卡状态。 */
    int updateCardStatusBatch(@Param("ids") List<Long> ids,
                              @Param("cardStatus") String cardStatus,
                              @Param("updateTime") LocalDateTime updateTime);

    /** 批量更新词汇进度。 */
    int updateBatch(@Param("items") List<LearningWordProgress> items);
}
