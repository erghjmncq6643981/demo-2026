package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointBindingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 坐席话机/分机绑定数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AgentEndpointBindingMapper extends BaseMapper<AgentEndpointBindingEntity> {

    /**
     * 锁定坐席主数据，串行化当前终端切换。
     *
     * @param workNo 坐席工号
     * @return 坐席主键；不存在时为空
     */
    Long lockAgentId(@Param("workNo") String workNo);

    /**
     * 统计禁止切换终端的通话中或整理中状态。
     *
     * @param agentId 坐席主键
     * @return 阻断状态数量
     */
    int countSwitchBlockingState(@Param("agentId") Long agentId);

    /**
     * 清除坐席当前接听终端标识。
     *
     * @param agentId 坐席主键
     * @return 更新数量
     */
    int deactivateByAgentId(@Param("agentId") Long agentId);

    /**
     * 激活指定的有效绑定。
     *
     * @param agentId 坐席主键
     * @param bindingId 绑定记录主键
     * @return 更新数量
     */
    int activate(@Param("agentId") Long agentId, @Param("bindingId") Long bindingId);
}
