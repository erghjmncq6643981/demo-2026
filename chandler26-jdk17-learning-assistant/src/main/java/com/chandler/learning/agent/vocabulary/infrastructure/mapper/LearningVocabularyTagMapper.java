package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Collection;

/**
 * LearningVocabularyTagMapper 类。
 */
@Mapper
public interface LearningVocabularyTagMapper extends BaseMapper<LearningVocabularyTag> {

    int physicalDeleteByVocabularyId(@Param("vocabularyId") Long vocabularyId);

    /** 批量删除词卡标签，避免批处理逐词删除。 */
    int physicalDeleteByVocabularyIds(@Param("vocabularyIds") Collection<Long> vocabularyIds);

    /** 批量读取词卡标签。 */
    List<LearningVocabularyTag> selectByVocabularyIds(@Param("vocabularyIds") Collection<Long> vocabularyIds);

    /** 批量保存词汇标签。 */
    int insertBatch(@Param("list") List<LearningVocabularyTag> list);
}
