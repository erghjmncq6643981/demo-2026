package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.OutboundNumberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外呼主叫号码池数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface OutboundNumberMapper extends BaseMapper<OutboundNumberEntity> {
}
