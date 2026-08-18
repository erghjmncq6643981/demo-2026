package com.chandler.learning.agent.mapper.vocabulary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogAnalysisBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCatalogAnalysisBatchMapper extends BaseMapper<VocabularyCatalogAnalysisBatch> {

    int insertBatch(@Param("list") List<VocabularyCatalogAnalysisBatch> list);
}
