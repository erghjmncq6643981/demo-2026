package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Collection;

/**
 * LearningVocabularyRelationMapper 类。
 */
@Mapper
public interface LearningVocabularyRelationMapper extends BaseMapper<LearningVocabularyRelation> {

    int physicalDeleteByVocabularyId(@Param("vocabularyId") Long vocabularyId);

    /** 批量删除词卡关联关系，避免批处理逐词删除。 */
    int physicalDeleteByVocabularyIds(@Param("vocabularyIds") Collection<Long> vocabularyIds);

    /** 批量读取词卡关联关系。 */
    List<LearningVocabularyRelation> selectByNormalizedTerms(@Param("normalizedTerms") Collection<String> normalizedTerms);

    /** 批量保存同义词、反义词和词族关系。 */
    int insertBatch(@Param("list") List<LearningVocabularyRelation> list);
}
