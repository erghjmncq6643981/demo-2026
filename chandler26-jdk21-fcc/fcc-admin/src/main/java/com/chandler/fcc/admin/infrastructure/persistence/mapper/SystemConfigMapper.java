package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.SystemConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态系统业务配置数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigEntity> {
}
