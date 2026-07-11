package com.chandler.learning.agent.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.learning.LearningVocabularyRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningVocabularyRelationMapper extends BaseMapper<LearningVocabularyRelation> {

    @Delete("DELETE FROM learning_vocabulary_relation WHERE vocabulary_id = #{vocabularyId}")
    int physicalDeleteByVocabularyId(@Param("vocabularyId") Long vocabularyId);
}
