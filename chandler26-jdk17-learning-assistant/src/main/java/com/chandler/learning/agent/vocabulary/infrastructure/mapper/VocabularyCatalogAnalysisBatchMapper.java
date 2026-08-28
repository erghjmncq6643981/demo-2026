package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogAnalysisBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCatalogAnalysisBatchMapper extends BaseMapper<VocabularyCatalogAnalysisBatch> {

    int insertBatch(@Param("list") List<VocabularyCatalogAnalysisBatch> list);
}
