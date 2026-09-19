package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席主数据数据访问 Mapper 接口
 * <p>
 * 提供坐席档案的新增、更新、软删除与多条件分页检索能力。
 * </p>
 *
 * @author Chandler
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}
