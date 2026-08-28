package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * EnglishVocabularyStudyRecordMapper 类。
 */
@Mapper
public interface EnglishVocabularyStudyRecordMapper extends BaseMapper<EnglishVocabularyStudyRecord> {

    /** 批量写入词卡缓存，竞争插入时保留已存在的缓存记录。 */
    int insertBatchIgnore(@Param("list") List<EnglishVocabularyStudyRecord> list);
}
