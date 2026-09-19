package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.FlowDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通话流程定义 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface FlowDefinitionMapper extends BaseMapper<FlowDefinitionEntity> {
}
