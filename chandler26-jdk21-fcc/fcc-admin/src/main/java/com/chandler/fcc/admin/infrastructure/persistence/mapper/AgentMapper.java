package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 记录坐席业务登录状态，不依赖 SIP/WebRTC 注册在线投影。
     *
     * @param agentId 坐席主键
     * @return 影响行数
     */
    int markLogin(@Param("agentId") Long agentId);

    /**
     * 记录坐席主动退出，并停止后续呼入分配。
     *
     * @param workNo 坐席工号
     * @return 影响行数
     */
    int markLogout(@Param("workNo") String workNo);

    /**
     * 多条件分页检索坐席列表
     */
    java.util.List<AgentEntity> selectAgentPage(
        @Param("agentName") String agentName,
        @Param("phoneNumber") String phoneNumber,
        @Param("workNo") String workNo,
        @Param("roleCode") String roleCode,
        @Param("status") String status,
        @Param("loginStatus") String loginStatus,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime,
        @Param("limit") long limit,
        @Param("offset") long offset
    );

    /**
     * 统计满足条件的坐席总数
     */
    long countAgentPage(
        @Param("agentName") String agentName,
        @Param("phoneNumber") String phoneNumber,
        @Param("workNo") String workNo,
        @Param("roleCode") String roleCode,
        @Param("status") String status,
        @Param("loginStatus") String loginStatus,
        @Param("startTime") java.time.LocalDateTime startTime,
        @Param("endTime") java.time.LocalDateTime endTime
    );

    /**
     * 批量查询坐席状态投影
     */
    java.util.List<java.util.Map<String, Object>> selectPresencesByAgentIds(@Param("agentIds") java.util.List<Long> agentIds);
}
