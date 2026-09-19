package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席技能组数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AgentGroupMapper extends BaseMapper<AgentGroupEntity> {
}
