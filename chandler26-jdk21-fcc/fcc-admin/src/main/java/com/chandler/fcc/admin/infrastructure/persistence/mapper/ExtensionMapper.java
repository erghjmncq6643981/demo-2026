package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ExtensionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通信分机数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface ExtensionMapper extends BaseMapper<ExtensionEntity> {
}
