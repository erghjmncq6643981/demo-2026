package com.chandler.learning.agent.vocabulary.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VocabularyCatalogMapper extends BaseMapper<VocabularyCatalog> {
}
