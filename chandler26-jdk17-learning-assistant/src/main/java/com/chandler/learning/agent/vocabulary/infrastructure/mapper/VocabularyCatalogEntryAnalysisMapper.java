package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntryAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCatalogEntryAnalysisMapper extends BaseMapper<VocabularyCatalogEntryAnalysis> {

    int insertBatch(@Param("list") List<VocabularyCatalogEntryAnalysis> list);
}
