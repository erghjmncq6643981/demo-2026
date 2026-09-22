package com.chandler.fcc.server.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.server.infrastructure.persistence.entity.EndpointRegistrationEventEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 终端注册状态事实数据访问接口。
 */
@Mapper
public interface EndpointRegistrationEventMapper extends BaseMapper<EndpointRegistrationEventEntity> {
}
