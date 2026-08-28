package com.chandler.learning.agent.vocabulary.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.VocabularyCardGenerationJobItem;
import com.chandler.learning.agent.vocabulary.domain.VocabularyCardGenerationProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCardGenerationJobItemMapper extends BaseMapper<VocabularyCardGenerationJobItem> {

    /** 批量创建词卡任务明细。 */
    int insertBatch(@Param("list") List<VocabularyCardGenerationJobItem> list);

    /** 批量刷新词卡任务明细状态。 */
    int updateBatch(@Param("list") List<VocabularyCardGenerationJobItem> list);

    /** 在数据库侧聚合任务进度，避免大任务全量加载明细。 */
    VocabularyCardGenerationProgress selectProgress(@Param("jobId") Long jobId);
}
