package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.reading.domain.bo.ArticleStudySummaryItem;
import com.chandler.learning.agent.reading.domain.entity.LearningArticleStudyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 语境精读数据访问接口。
 */
@Mapper
public interface LearningArticleStudyRecordMapper extends BaseMapper<LearningArticleStudyRecord> {

    /** 分页读取精读历史摘要，文章正文 JSON 仅在详情接口查询。 */
    Page<ArticleStudySummaryItem> selectSummaryPage(
            Page<ArticleStudySummaryItem> page,
            @Param("userId") Long userId,
            @Param("wordbookId") Long wordbookId);
}
