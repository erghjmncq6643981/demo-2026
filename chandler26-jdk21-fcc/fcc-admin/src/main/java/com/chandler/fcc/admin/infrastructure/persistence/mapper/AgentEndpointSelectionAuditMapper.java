package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointSelectionAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席当前接听终端切换审计数据访问接口。
 */
@Mapper
public interface AgentEndpointSelectionAuditMapper extends BaseMapper<AgentEndpointSelectionAuditEntity> {
}
