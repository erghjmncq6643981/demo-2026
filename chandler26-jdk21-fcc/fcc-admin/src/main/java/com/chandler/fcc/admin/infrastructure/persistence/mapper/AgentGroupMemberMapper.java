package com.chandler.fcc.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chandler.fcc.admin.infrastructure.persistence.data.AgentGroupMemberRow;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupMemberEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 坐席技能组成员关联数据访问 Mapper 接口
 *
 * @author Chandler
 */
@Mapper
public interface AgentGroupMemberMapper extends BaseMapper<AgentGroupMemberEntity> {

    /**
     * 统计指定组织节点及全部启用子节点中的去重坐席数量。
     *
     * @param groupId 所选组织节点主键
     * @param keyword 坐席姓名、工号或手机号搜索词，可为空
     * @return 符合条件的去重坐席数量
     */
    long countSubtreeMembers(
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword
    );

    /**
     * 分页查询指定组织节点及全部启用子节点中的坐席。
     *
     * @param groupId 所选组织节点主键
     * @param keyword 坐席姓名、工号或手机号搜索词，可为空
     * @param offset 分页偏移
     * @param limit 每页条数
     * @return 去重后的成员查询结果
     */
    List<AgentGroupMemberRow> selectSubtreeMembers(
            @Param("groupId") Long groupId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}
