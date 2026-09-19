package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentSubstituteRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席替班记录数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AgentSubstituteRecordMapper extends BaseMapper<AgentSubstituteRecordEntity> {
}
