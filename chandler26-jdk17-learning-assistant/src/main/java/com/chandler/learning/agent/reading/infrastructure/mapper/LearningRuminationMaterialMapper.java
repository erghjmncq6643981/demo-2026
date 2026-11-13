package com.chandler.learning.agent.reading.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识反刍场景材料文章数据访问接口。
 */
@Mapper
public interface LearningRuminationMaterialMapper extends BaseMapper<LearningRuminationMaterial> {
}
