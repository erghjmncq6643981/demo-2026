package com.chandler.learning.agent.mapper.vocabulary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCardGenerationJobItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCardGenerationJobItemMapper extends BaseMapper<VocabularyCardGenerationJobItem> {

    /** 批量创建词卡任务明细。 */
    int insertBatch(@Param("list") List<VocabularyCardGenerationJobItem> list);

    /** 批量刷新词卡任务明细状态。 */
    int updateBatch(@Param("list") List<VocabularyCardGenerationJobItem> list);
}
