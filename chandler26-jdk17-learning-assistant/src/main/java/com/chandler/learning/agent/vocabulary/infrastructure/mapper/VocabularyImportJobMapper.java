package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyImportJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VocabularyImportJobMapper extends BaseMapper<VocabularyImportJob> {
}
