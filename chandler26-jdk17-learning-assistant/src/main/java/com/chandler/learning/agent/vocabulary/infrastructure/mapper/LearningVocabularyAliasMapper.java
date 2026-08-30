package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyAlias;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 英语词汇形态变形与别名数据访问接口。
 */
@Mapper
public interface LearningVocabularyAliasMapper extends BaseMapper<LearningVocabularyAlias> {

    /** 根据归一化别名查询匹配记录。 */
    LearningVocabularyAlias findByNormalizedAlias(@Param("normalizedAlias") String normalizedAlias);

    /** 批量根据归一化别名查询匹配记录。 */
    List<LearningVocabularyAlias> findByNormalizedAliases(@Param("normalizedAliases") Collection<String> normalizedAliases);

    /** 批量删除指定词卡的别名索引。 */
    int physicalDeleteByVocabularyIds(@Param("vocabularyIds") Collection<Long> vocabularyIds);

    /** 批量新增别名记录（支持忽略重复项）。 */
    int insertBatch(@Param("list") List<LearningVocabularyAlias> list);
}
