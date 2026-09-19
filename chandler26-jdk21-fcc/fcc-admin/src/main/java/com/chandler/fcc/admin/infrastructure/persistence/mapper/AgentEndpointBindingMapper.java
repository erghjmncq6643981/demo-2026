package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席话机/分机绑定数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AgentEndpointBindingMapper extends BaseMapper<AgentEndpointBindingEntity> {
}
