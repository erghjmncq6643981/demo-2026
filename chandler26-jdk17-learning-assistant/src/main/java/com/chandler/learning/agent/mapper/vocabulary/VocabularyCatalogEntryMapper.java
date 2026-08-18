package com.chandler.learning.agent.mapper.vocabulary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCatalogEntryMapper extends BaseMapper<VocabularyCatalogEntry> {

    int insertBatch(@Param("list") List<VocabularyCatalogEntry> list);

    /** 批量确认疑似断词，单批数据由服务层控制在安全范围内。 */
    int updateReviewBatch(@Param("list") List<VocabularyCatalogEntry> list);

    /** 批量把词表词条标记为已发布。 */
    int markPublishedBatch(@Param("ids") List<Long> ids,
                           @Param("updateTime") java.time.LocalDateTime updateTime,
                           @Param("updateBy") Long updateBy);
}
