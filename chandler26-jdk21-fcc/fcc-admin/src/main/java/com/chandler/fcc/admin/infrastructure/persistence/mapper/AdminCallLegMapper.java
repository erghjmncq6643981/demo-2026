package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallLegEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理台通话分段 Leg 数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AdminCallLegMapper extends BaseMapper<CallLegEntity> {
}
