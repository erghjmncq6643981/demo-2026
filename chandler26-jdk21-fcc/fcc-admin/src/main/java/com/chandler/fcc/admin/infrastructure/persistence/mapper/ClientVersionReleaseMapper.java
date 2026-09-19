package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.ClientVersionReleaseEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端版本发布数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface ClientVersionReleaseMapper extends BaseMapper<ClientVersionReleaseEntity> {
}
