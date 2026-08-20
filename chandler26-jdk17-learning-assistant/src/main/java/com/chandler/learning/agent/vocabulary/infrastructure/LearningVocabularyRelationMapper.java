package com.chandler.learning.agent.vocabulary.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.LearningVocabularyRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * LearningVocabularyRelationMapper 类。
 */
@Mapper
public interface LearningVocabularyRelationMapper extends BaseMapper<LearningVocabularyRelation> {

    int physicalDeleteByVocabularyId(@Param("vocabularyId") Long vocabularyId);

    /** 批量保存同义词、反义词和词族关系。 */
    int insertBatch(@Param("list") List<LearningVocabularyRelation> list);
}
