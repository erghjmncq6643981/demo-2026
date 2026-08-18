package com.chandler.learning.agent.mapper.vocabulary;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntryAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyCatalogEntryAnalysisMapper extends BaseMapper<VocabularyCatalogEntryAnalysis> {

    int insertBatch(@Param("list") List<VocabularyCatalogEntryAnalysis> list);
}
