package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupEntity;
import com.chandler.fcc.admin.infrastructure.persistence.data.AgentGroupCountRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 坐席技能组数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AgentGroupMapper extends BaseMapper<AgentGroupEntity> {

    /**
     * 一次查询全部启用组织节点的子树去重坐席数量。
     *
     * @return 节点主键及子树成员数量
     */
    List<AgentGroupCountRow> selectSubtreeMemberCounts();
}
