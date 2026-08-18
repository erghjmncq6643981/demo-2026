package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningVocabularyTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LearningVocabularyTagMapper 类。
 */
@Mapper
public interface LearningVocabularyTagMapper extends BaseMapper<LearningVocabularyTag> {

    int physicalDeleteByVocabularyId(@Param("vocabularyId") Long vocabularyId);

    /** 批量保存词汇标签。 */
    int insertBatch(@Param("list") List<LearningVocabularyTag> list);
}
