package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallbackTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 漏话回拨待办总池数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface CallbackTaskMapper extends BaseMapper<CallbackTaskEntity> {
}
