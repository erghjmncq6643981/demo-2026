package com.chandler.learning.agent.vocabulary.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.LearningWordbook;
import com.chandler.learning.agent.vocabulary.api.WordbookResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LearningWordbookMapper 类。
 */
@Mapper
public interface LearningWordbookMapper extends BaseMapper<LearningWordbook> {

    /** 批量查询单词本及词条、待复习数量，避免列表转换产生 N+1。 */
    List<WordbookResponse> selectWordbookSummaries(@Param("userId") Long userId);
}
