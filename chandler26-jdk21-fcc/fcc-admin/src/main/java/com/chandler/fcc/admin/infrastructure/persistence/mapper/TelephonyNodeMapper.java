package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.TelephonyNodeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通信节点注册数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface TelephonyNodeMapper extends BaseMapper<TelephonyNodeEntity> {
}
