package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.TelephonyTrunkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * SIP 中继线路与运营商网关数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface TelephonyTrunkMapper extends BaseMapper<TelephonyTrunkEntity> {
}
