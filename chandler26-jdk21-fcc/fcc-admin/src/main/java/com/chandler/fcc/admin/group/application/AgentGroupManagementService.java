package com.chandler.fcc.admin.group.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.data.AgentGroupMemberRow;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupMemberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentGroupMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentGroupMemberMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.AgentGroupCreateReq;
import com.chandler.fcc.admin.model.dto.AgentGroupUpdateReq;
import com.chandler.fcc.admin.model.dto.AgentGroupMemberReq;
import com.chandler.fcc.admin.model.dto.AgentGroupMemberUpdateReq;
import com.chandler.fcc.admin.model.vo.AgentGroupMemberVO;
import com.chandler.fcc.admin.model.vo.AgentGroupVO;
import com.chandler.fcc.common.util.IdUtil;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组织与技能组成员管理；账号创建仍由坐席用例编排，事务在公开应用方法上生效。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGroupManagementService {
    private final AgentGroupMapper groupMapper;
    private final AgentGroupMemberMapper groupMemberMapper;

    /**
     * 系统启动检查并初始化顶级企业根节点 (仅当数据库为空时)
     */
    @PostConstruct
    public void initRootGroupIfAbsent() {
        Long count = groupMapper.selectCount(new LambdaQueryWrapper<AgentGroupEntity>().isNull(AgentGroupEntity::getDeletedAt));
        if (count == 0) {
            AgentGroupEntity root = AgentGroupEntity.builder()
                    .id(1L)
                    .parentId(0L)
                    .groupCode("ROOT_ORG")
                    .groupName("箱箱物流科技")
                    .groupType("COMPANY")
                    .routingStrategy("ROUND_ROBIN")
                    .status("ENABLED")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            groupMapper.insert(root);
            log.info("[AgentService] 成功在数据库中初始化顶级企业根节点: id=1, name=箱箱物流科技");
        }
    }

    /**
     * 创建技能组
     *
     * @param req 技能组创建入参
     * @return 技能组雪花主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(AgentGroupCreateReq req) {
        Long groupId = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();

        AgentGroupEntity group = AgentGroupEntity.builder()
                .id(groupId)
                .parentId(req.getParentId() == null ? 0L : req.getParentId())
                .groupCode(req.getGroupCode().trim())
                .groupName(req.getGroupName().trim())
                .groupType(req.getGroupType() == null ? "SKILL" : req.getGroupType())
                .routingStrategy(req.getRoutingStrategy() == null ? "LONGEST_IDLE" : req.getRoutingStrategy())
                .status("ENABLED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        groupMapper.insert(group);
        log.info("[AgentService] 成功创建技能组: id={}, code={}, name={}, parentId={}", groupId, req.getGroupCode(), req.getGroupName(), group.getParentId());
        return groupId;
    }

    /**
     * 修改技能组信息
     *
     * @param req 技能组修改入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGroup(AgentGroupUpdateReq req) {
        AgentGroupEntity group = groupMapper.selectById(req.getId());
        if (group == null || group.getDeletedAt() != null) {
            throw new IllegalArgumentException("技能组不存在: id=" + req.getId());
        }
        if (req.getGroupName() != null && !req.getGroupName().isBlank()) {
            group.setGroupName(req.getGroupName().trim());
        }
        if (req.getGroupCode() != null && !req.getGroupCode().isBlank()) {
            group.setGroupCode(req.getGroupCode().trim());
        }
        if (req.getGroupType() != null && !req.getGroupType().isBlank()) {
            group.setGroupType(req.getGroupType().trim());
        }
        if (req.getRoutingStrategy() != null && !req.getRoutingStrategy().isBlank()) {
            group.setRoutingStrategy(req.getRoutingStrategy().trim());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            group.setStatus(req.getStatus().trim());
        }
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.updateById(group);
        log.info("[AgentService] 成功更新技能组: id={}, name={}", group.getId(), group.getGroupName());
    }

    /**
     * 删除技能组 (禁止删除顶级企业根节点)
     *
     * @param id 技能组 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        AgentGroupEntity group = groupMapper.selectById(id);
        if (group == null || group.getDeletedAt() != null) {
            return;
        }
        if (group.getParentId() == null || group.getParentId() == 0L || Long.valueOf(1L).equals(group.getId())) {
            throw new IllegalArgumentException("顶级企业根节点受保护，不允许删除！");
        }
        LocalDateTime now = LocalDateTime.now();
        group.setDeletedAt(now);
        group.setStatus("DISABLED");
        groupMapper.updateById(group);

        // 级联软删除子部门与技能组
        List<AgentGroupEntity> children = groupMapper.selectList(new LambdaQueryWrapper<AgentGroupEntity>()
                .isNull(AgentGroupEntity::getDeletedAt)
                .eq(AgentGroupEntity::getParentId, id));
        for (AgentGroupEntity child : children) {
            child.setDeletedAt(now);
            child.setStatus("DISABLED");
            groupMapper.updateById(child);
            // 移除子组成员
            groupMemberMapper.delete(new LambdaQueryWrapper<AgentGroupMemberEntity>().eq(AgentGroupMemberEntity::getGroupId, child.getId()));
        }

        // 移除本组成员
        groupMemberMapper.delete(new LambdaQueryWrapper<AgentGroupMemberEntity>().eq(AgentGroupMemberEntity::getGroupId, id));
        log.info("[AgentService] 成功删除技能组: id={}, name={}", id, group.getGroupName());
    }

    /**
     * 获取全量技能组列表
     *
     * @return 技能组展示列表 (含 parentId 与真实成员数)
     */
    public List<AgentGroupVO> listGroups() {
        List<AgentGroupEntity> groups = groupMapper.selectList(new LambdaQueryWrapper<AgentGroupEntity>()
                .isNull(AgentGroupEntity::getDeletedAt)
                .eq(AgentGroupEntity::getStatus, "ENABLED")
                .orderByAsc(AgentGroupEntity::getId));
        Map<Long, Long> subtreeMemberCounts = groupMapper.selectSubtreeMemberCounts().stream()
                .collect(Collectors.toMap(
                        row -> row.getGroupId(),
                        row -> row.getMemberCount() == null ? 0L : row.getMemberCount()
                ));

        return groups.stream()
                .map(group -> AgentGroupVO.builder()
                        .id(group.getId())
                        .parentId(group.getParentId() == null ? 0L : group.getParentId())
                        .groupCode(group.getGroupCode())
                        .groupName(group.getGroupName())
                        .groupType(group.getGroupType())
                        .routingStrategy(group.getRoutingStrategy())
                        .status(group.getStatus())
                        .memberCount(subtreeMemberCounts.getOrDefault(group.getId(), 0L).intValue())
                        .createdAt(group.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * 分页查询指定组织节点及全部启用子节点中的去重坐席。
     *
     * @param groupId 技能组 ID
     * @param pageNum 页码，从一开始
     * @param pageSize 每页条数
     * @param keyword 坐席姓名、工号或手机号搜索词，可为空
     * @return 子树成员分页结果
     */
    public PageResult<AgentGroupMemberVO> listGroupMembers(
            Long groupId,
            int pageNum,
            int pageSize,
            String keyword
    ) {
        AgentGroupEntity group = groupMapper.selectById(groupId);
        if (group == null || group.getDeletedAt() != null || !"ENABLED".equals(group.getStatus())) {
            throw new IllegalArgumentException("组织或技能组不存在: id=" + groupId);
        }
        if (pageNum < 1 || pageNum > 10_000 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("成员分页参数不合法");
        }
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 64) {
            throw new IllegalArgumentException("成员搜索词不能超过64个字符");
        }

        long total = groupMemberMapper.countSubtreeMembers(groupId, normalizedKeyword);
        if (total == 0L) {
            return PageResult.empty(pageNum, pageSize);
        }
        long offset = (long) (pageNum - 1) * pageSize;
        List<AgentGroupMemberVO> members = groupMemberMapper.selectSubtreeMembers(
                        groupId,
                        normalizedKeyword,
                        offset,
                        pageSize
                ).stream()
                .map(this::toGroupMemberVO)
                .toList();
        return PageResult.<AgentGroupMemberVO>builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .list(members)
                .build();
    }

    /**
     * 将持久化查询行转换为管理端成员视图。
     *
     * @param row 子树成员查询行
     * @return 管理端成员视图
     */
    private AgentGroupMemberVO toGroupMemberVO(AgentGroupMemberRow row) {
        return AgentGroupMemberVO.builder()
                .id(row.getMembershipId() == null ? null : String.valueOf(row.getMembershipId()))
                .groupId(row.getGroupId() == null ? null : String.valueOf(row.getGroupId()))
                .groupName(row.getGroupName())
                .agentId(row.getAgentId() == null ? null : String.valueOf(row.getAgentId()))
                .workNo(row.getWorkNo())
                .agentName(row.getAgentName())
                .phoneNumber(row.getPhoneNumber())
                .memberRole(row.getMemberRole())
                .priority(row.getPriority())
                .roleCode(row.getRoleCode())
                .status(row.getStatus())
                .createdAt(row.getCreatedAt())
                .build();
    }

    /**
     * 分配坐席至技能组 (绑定已有坐席)
     *
     * @param req 组员分配入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMemberToGroup(AgentGroupMemberReq req) {
        LambdaQueryWrapper<AgentGroupMemberEntity> wrapper = new LambdaQueryWrapper<AgentGroupMemberEntity>()
                .eq(AgentGroupMemberEntity::getGroupId, req.getGroupId())
                .eq(AgentGroupMemberEntity::getAgentId, req.getAgentId());

        if (groupMemberMapper.selectCount(wrapper) > 0) {
            return;
        }

        AgentGroupMemberEntity member = AgentGroupMemberEntity.builder()
                .id(IdUtil.nextId())
                .groupId(req.getGroupId())
                .agentId(req.getAgentId())
                .memberRole(req.getMemberRole() == null || req.getMemberRole().isBlank() ? "MEMBER" : req.getMemberRole())
                .priority(req.getPriority() == null ? 0 : req.getPriority())
                .createdAt(LocalDateTime.now())
                .build();

        groupMemberMapper.insert(member);
        log.info("[AgentService] 坐席加入技能组: agentId={}, groupId={}", req.getAgentId(), req.getGroupId());
    }

    /**
     * 更新技能组成员属性 (角色/优先级)
     *
     * @param groupId 技能组 ID
     * @param agentId 坐席 ID
     * @param req 成员属性更新入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateGroupMember(Long groupId, Long agentId, AgentGroupMemberUpdateReq req) {
        AgentGroupMemberEntity member = groupMemberMapper.selectOne(new LambdaQueryWrapper<AgentGroupMemberEntity>()
                .eq(AgentGroupMemberEntity::getGroupId, groupId)
                .eq(AgentGroupMemberEntity::getAgentId, agentId));
        if (member == null) {
            throw new IllegalArgumentException("该坐席不在当前技能组内");
        }
        if (req.getMemberRole() != null && !req.getMemberRole().isBlank()) {
            member.setMemberRole(req.getMemberRole().trim());
        }
        if (req.getPriority() != null) {
            member.setPriority(req.getPriority());
        }
        groupMemberMapper.updateById(member);
        log.info("[AgentService] 成功更新组成员属性: groupId={}, agentId={}", groupId, agentId);
    }

    /**
     * 从技能组中移除坐席 (解绑)
     *
     * @param groupId 技能组 ID
     * @param agentId 坐席 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMemberFromGroup(Long groupId, Long agentId) {
        groupMemberMapper.delete(new LambdaQueryWrapper<AgentGroupMemberEntity>()
                .eq(AgentGroupMemberEntity::getGroupId, groupId)
                .eq(AgentGroupMemberEntity::getAgentId, agentId));
        log.info("[AgentService] 坐席退出技能组: agentId={}, groupId={}", agentId, groupId);
    }

}
