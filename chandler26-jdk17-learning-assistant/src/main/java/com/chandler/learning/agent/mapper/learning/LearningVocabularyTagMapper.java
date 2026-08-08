package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningVocabularyTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * LearningVocabularyTagMapper 类。
 */
@Mapper
public interface LearningVocabularyTagMapper extends BaseMapper<LearningVocabularyTag> {

    @Delete("DELETE FROM learning_vocabulary_tag WHERE vocabulary_id = #{vocabularyId}")
    int physicalDeleteByVocabularyId(@Param("vocabularyId") Long vocabularyId);
}
