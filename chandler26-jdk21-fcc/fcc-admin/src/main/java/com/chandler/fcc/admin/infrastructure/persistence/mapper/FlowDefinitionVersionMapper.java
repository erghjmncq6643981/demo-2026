package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话流程版本快照 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface FlowDefinitionVersionMapper extends BaseMapper<FlowDefinitionVersionEntity> {
}
