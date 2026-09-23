package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.client.SidecarAdminClient;
import com.chandler.fcc.admin.infrastructure.persistence.data.AgentGroupMemberRow;
import com.chandler.fcc.admin.infrastructure.persistence.entity.*;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.*;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.*;
import com.chandler.fcc.admin.model.enums.AgentRoleEnum;
import com.chandler.fcc.admin.model.enums.AuthRoleEnum;
import com.chandler.fcc.admin.model.vo.AccountCredentialVO;
import com.chandler.fcc.admin.model.vo.AgentBindingVO;
import com.chandler.fcc.admin.model.vo.AgentGroupMemberVO;
import com.chandler.fcc.admin.model.vo.AgentGroupVO;
import com.chandler.fcc.admin.model.vo.AgentSubstituteVO;
import com.chandler.fcc.admin.model.vo.AgentVO;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.common.util.PasswordHasher;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 坐席人员、技能组、终端绑定与替班全生命周期业务服务
 * <p>
 * 坐席档案与登录账号统一以 fcc_agent 表为唯一数据基准，不设任何人员准入名单：
 * 新增坐席时写入 PBKDF2 口令派生串；口令留空则生成一次性随机口令返回给调用方。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final SipCredentialCipher credentialCipher;

    /**
     * 系统生成的坐席初始口令长度
     */
    private static final int GENERATED_PASSWORD_LENGTH = 12;

    /**
     * 系统生成的分机注册口令长度
     */
    private static final int EXTENSION_SECRET_LENGTH = 16;

    private final AgentMapper agentMapper;
    private final AgentGroupMapper groupMapper;
    private final AgentGroupMemberMapper groupMemberMapper;
    private final AgentEndpointBindingMapper bindingMapper;
    private final AgentSubstituteRecordMapper substituteRecordMapper;
    private final ExtensionMapper extensionMapper;
    private final SidecarAdminClient sidecarAdminClient;

    /**
     * 新增坐席人员信息
     * <p>
     * 同时开通该坐席的工作台登录账号：口令以 PBKDF2 派生串写入 fcc_agent.password_hash。
     * 入参未提供口令时生成一次性随机口令，通过返回值交付一次。
     * </p>
     *
     * @param req 坐席创建入参
     * @return 坐席创建结果 (含一次性初始口令)
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialVO createAgent(AgentCreateReq req) {
        String workNo = req.getWorkNo().trim();

        // 1. 工号全局唯一性校验
        LambdaQueryWrapper<AgentEntity> checkWrapper = new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkNo, workNo);
        if (agentMapper.selectCount(checkWrapper) > 0) {
            throw new IllegalArgumentException("坐席工号已存在: " + workNo);
        }

        // 2. 角色码归一化：非法或空值一律回落为普通坐席，避免脏数据越权
        String roleCode = AgentRoleEnum.normalizeCode(req.getRoleCode());

        // 3. 口令派生：未指定时生成一次性随机口令
        boolean passwordGenerated = req.getPassword() == null || req.getPassword().isBlank();
        String plainPassword = passwordGenerated
                ? PasswordHasher.generateInitialPassword(GENERATED_PASSWORD_LENGTH)
                : req.getPassword();

        Long agentId = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();

        AgentEntity entity = AgentEntity.builder()
                .id(agentId)
                .workNo(workNo)
                .agentName(req.getAgentName().trim())
                .phoneNumber(req.getPhoneNumber())
                .roleCode(roleCode)
                .status("ENABLED")
                .passwordHash(PasswordHasher.hash(plainPassword))
                .passwordUpdatedAt(now)
                .metadata(req.getMetadata())
                .createdAt(now)
                .updatedAt(now)
                .build();

        agentMapper.insert(entity);
        log.info("[AgentService] 成功创建坐席: id={}, workNo={}, name={}, role={}",
                agentId, workNo, req.getAgentName(), roleCode);

        // 自动为坐席工号开通 WebRTC 软话机分机。
        // 分机注册口令随机生成并随分机一并落库，取代以往全体共用的固定口令。
        String registrationSecret = PasswordHasher.generateInitialPassword(EXTENSION_SECRET_LENGTH);
        byte[] encryptedSecret = credentialCipher.encrypt(registrationSecret);
        boolean provisioned = false;
        try {
            var response = sidecarAdminClient.createExtension(workNo, registrationSecret);
            provisioned = response != null && (response.getCode() == 200 || response.getCode() == 0);
        } catch (Exception e) {
            log.warn("[AgentService] 同步工号分机至 FreeSWITCH 告警: {}", e.getMessage());
        }

        ExtensionEntity existingExtension = extensionMapper.selectOne(new LambdaQueryWrapper<ExtensionEntity>()
                .eq(ExtensionEntity::getExtension, workNo));
        if (existingExtension == null) {
            extensionMapper.insert(ExtensionEntity.builder()
                    .id(IdUtil.nextId())
                    .extension(workNo)
                    .endpointType("WEBRTC")
                    .credentialSecret(encryptedSecret)
                    .status(provisioned ? "ENABLED" : "PROVISIONING_FAILED")
                    .agentWorkNo(workNo)
                    .agentName(entity.getAgentName())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } else {
            existingExtension.setCredentialSecret(encryptedSecret);
            existingExtension.setStatus(provisioned ? "ENABLED" : "PROVISIONING_FAILED");
            existingExtension.setAgentWorkNo(workNo);
            existingExtension.setAgentName(entity.getAgentName());
            existingExtension.setUpdatedAt(now);
            extensionMapper.updateById(existingExtension);
        }

        // 新坐席以 WebRTC 作为唯一当前接听终端；物理 SIP 必须由话机拨 0000 绑定。
        bindingMapper.insert(AgentEndpointBindingEntity.builder()
                .id(IdUtil.nextId())
                .agentId(agentId)
                .endpointType("WEBRTC")
                .endpointValue(workNo)
                .priority(0)
                .active(true)
                .status("ENABLED")
                .validFrom(now)
                .createdAt(now)
                .build());

        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            bindingMapper.insert(AgentEndpointBindingEntity.builder()
                    .id(IdUtil.nextId())
                    .agentId(agentId)
                    .endpointType("MOBILE")
                    .endpointValue(req.getPhoneNumber().trim())
                    .priority(2)
                    .status("ENABLED")
                    .validFrom(now)
                    .createdAt(now)
                    .build());
        }

        return AccountCredentialVO.builder()
                .subjectType("AGENT")
                .id(agentId)
                .account(workNo)
                .displayName(entity.getAgentName())
                .initialPassword(passwordGenerated ? plainPassword : null)
                .hint(passwordGenerated
                        ? "请立即将初始口令交付本人并提醒其首次登录后修改"
                        : "口令已按指定值设置")
                .build();
    }

    /**
     * 更新坐席资料
     *
     * @param req 坐席更新入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAgent(AgentUpdateReq req) {
        AgentEntity existing = agentMapper.selectById(req.getId());
        if (existing == null) {
            throw new IllegalArgumentException("坐席不存在: id=" + req.getId());
        }

        if (req.getAgentName() != null && !req.getAgentName().isBlank()) {
            validateAgentName(req.getAgentName());
            existing.setAgentName(req.getAgentName().trim());
        }
        if (req.getPhoneNumber() != null) {
            existing.setPhoneNumber(req.getPhoneNumber().trim());
        }
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()) {
            existing.setRoleCode(AgentRoleEnum.normalizeCode(req.getRoleCode()));
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            existing.setStatus(req.getStatus().trim().toUpperCase());
        }
        if (req.getMetadata() != null) {
            existing.setMetadata(req.getMetadata());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(existing);
        log.info("[AgentService] 成功更新坐席: id={}, name={}", req.getId(), existing.getAgentName());
    }

    /**
     * 重置坐席登录口令
     * <p>
     * 口令留空时生成一次性随机口令，通过返回值交付；系统不保留明文。
     * </p>
     *
     * @param agentId     坐席主键 ID
     * @param rawPassword 新口令；为空则生成随机口令
     * @return 口令重置结果 (含一次性新口令)
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialVO resetPassword(Long agentId, String rawPassword) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null || agent.getDeletedAt() != null) {
            throw new IllegalArgumentException("坐席不存在: id=" + agentId);
        }

        boolean generated = rawPassword == null || rawPassword.isBlank();
        String plainPassword = generated
                ? PasswordHasher.generateInitialPassword(GENERATED_PASSWORD_LENGTH)
                : rawPassword;

        LocalDateTime now = LocalDateTime.now();
        agent.setPasswordHash(PasswordHasher.hash(plainPassword));
        agent.setPasswordUpdatedAt(now);
        agent.setUpdatedAt(now);
        agentMapper.updateById(agent);
        log.info("[AgentService] 已重置坐席登录口令: id={}, workNo={}", agentId, agent.getWorkNo());

        return AccountCredentialVO.builder()
                .subjectType("AGENT")
                .id(agent.getId())
                .account(agent.getWorkNo())
                .displayName(agent.getAgentName())
                .initialPassword(generated ? plainPassword : null)
                .hint("口令已重置，请通知坐席重新登录")
                .build();
    }

    /**
     * 多条件分页查询坐席列表
     *
     * @param req 分页过滤入参
     * @return 坐席展示视图分页容器
     */
    public PageResult<AgentVO> queryAgents(AgentQueryReq req) {
        Page<AgentEntity> page = new Page<>(req.getPageNum(), req.getPageSize());
        LambdaQueryWrapper<AgentEntity> wrapper = new LambdaQueryWrapper<AgentEntity>()
                .isNull(AgentEntity::getDeletedAt)
                .eq(req.getWorkNo() != null && !req.getWorkNo().isBlank(), AgentEntity::getWorkNo, req.getWorkNo())
                .like(req.getAgentName() != null && !req.getAgentName().isBlank(), AgentEntity::getAgentName, req.getAgentName())
                .like(req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank(), AgentEntity::getPhoneNumber, req.getPhoneNumber())
                .eq(req.getRoleCode() != null && !req.getRoleCode().isBlank(), AgentEntity::getRoleCode, req.getRoleCode())
                .eq(req.getStatus() != null && !req.getStatus().isBlank(), AgentEntity::getStatus, req.getStatus())
                .orderByDesc(AgentEntity::getCreatedAt);

        Page<AgentEntity> entityPage = agentMapper.selectPage(page, wrapper);

        List<AgentVO> voList = entityPage.getRecords().stream()
                .map(this::convertToVO)
                .toList();

        return PageResult.<AgentVO>builder()
                .pageNum(entityPage.getCurrent())
                .pageSize(entityPage.getSize())
                .total(entityPage.getTotal())
                .list(voList)
                .build();
    }

    /**
     * 根据主键 ID 获取坐席详情
     *
     * @param id 坐席主键 ID
     * @return 坐席 VO 实体
     */
    public AgentVO getAgentById(Long id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null || entity.getDeletedAt() != null) {
            return null;
        }
        return convertToVO(entity);
    }

    /**
     * 软删除坐席人员
     *
     * @param id 坐席主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(Long id) {
        AgentEntity agent = agentMapper.selectById(id);
        if (agent != null) {
            agent.setStatus("DISABLED");
            agent.setDeletedAt(LocalDateTime.now());
            agentMapper.updateById(agent);
            log.info("[AgentService] 成功软删除坐席: id={}", id);
        }
    }

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
     * 创建新坐席并直接加入技能组
     *
     * @param groupId 技能组 ID
     * @param req 创建及绑定入参
     * @return 新增坐席主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialVO createAndBindAgent(Long groupId, AgentCreateAndBindGroupReq req) {
        AgentCreateReq agentReq = AgentCreateReq.builder()
                .workNo(req.getWorkNo().trim())
                .agentName(req.getAgentName().trim())
                .phoneNumber(req.getPhoneNumber())
                .roleCode(AgentRoleEnum.normalizeCode(req.getRoleCode()))
                .password(req.getPassword())
                .build();
        AccountCredentialVO credential = createAgent(agentReq);
        Long agentId = credential.getId();

        AgentGroupMemberReq memberReq = AgentGroupMemberReq.builder()
                .groupId(groupId)
                .agentId(agentId)
                .memberRole(req.getMemberRole() == null || req.getMemberRole().isBlank() ? "MEMBER" : req.getMemberRole())
                .priority(req.getPriority() == null ? 0 : req.getPriority())
                .build();
        addMemberToGroup(memberReq);

        log.info("[AgentService] 成功创建坐席并绑定至组: agentId={}, workNo={}, groupId={}", agentId, req.getWorkNo(), groupId);
        return credential;
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

    /**
     * 查询坐席终端绑定记录
     *
     * @param agentId 坐席 ID
     * @return 绑定记录列表
     */
    public List<AgentBindingVO> listBindingsByAgentId(Long agentId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        String agentName = agent != null ? agent.getAgentName() : "";
        String workNo = agent != null ? agent.getWorkNo() : "";

        List<AgentEndpointBindingEntity> list = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                        .eq(AgentEndpointBindingEntity::getAgentId, agentId)
                        .orderByAsc(AgentEndpointBindingEntity::getPriority));

        return list.stream().map(b -> AgentBindingVO.builder()
                .id(b.getId())
                .agentId(b.getAgentId())
                .agentName(agentName)
                .workNo(workNo)
                .endpointType(b.getEndpointType())
                .extensionId(b.getExtensionId())
                .endpointValue(b.getEndpointValue())
                .priority(b.getPriority())
                .active(b.getActive())
                .status(b.getStatus())
                .validFrom(b.getValidFrom())
                .validTo(b.getValidTo())
                .build()
        ).toList();
    }

    /**
     * 申请坐席替班/夜班代接
     *
     * @param req 替班申请入参
     * @return 替班记录主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long applySubstitute(AgentSubstituteReq req) {
        AgentEntity applicant = agentMapper.selectById(req.getApplicantAgentId());
        if (applicant == null) {
            throw new IllegalArgumentException("申请人坐席不存在: id=" + req.getApplicantAgentId());
        }
        AgentEntity substitute = agentMapper.selectById(req.getSubstituteAgentId());
        if (substitute == null) {
            throw new IllegalArgumentException("代接人坐席不存在: id=" + req.getSubstituteAgentId());
        }

        Long substituteId = IdUtil.nextId();
        LocalDateTime now = LocalDateTime.now();

        AgentSubstituteRecordEntity record = AgentSubstituteRecordEntity.builder()
                .id(substituteId)
                .applicantAgentId(req.getApplicantAgentId())
                .substituteAgentId(req.getSubstituteAgentId())
                .substituteType(req.getSubstituteType())
                .scope(req.getScope())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .priority(req.getPriority())
                .status("CONFIRMED") // 默认批准生效
                .reason(req.getReason())
                .confirmedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        substituteRecordMapper.insert(record);
        log.info("[AgentService] 替班申请生效: id={}, applicant={}, substitute={}",
                substituteId, applicant.getAgentName(), substitute.getAgentName());
        return substituteId;
    }

    /**
     * 查询替班代接记录列表
     *
     * @return 替班记录 VO 列表
     */
    public List<AgentSubstituteVO> listSubstitutes() {
        List<AgentSubstituteRecordEntity> records = substituteRecordMapper.selectList(
                new LambdaQueryWrapper<AgentSubstituteRecordEntity>()
                        .orderByDesc(AgentSubstituteRecordEntity::getCreatedAt));

        return records.stream().map(r -> {
            AgentEntity app = agentMapper.selectById(r.getApplicantAgentId());
            AgentEntity sub = agentMapper.selectById(r.getSubstituteAgentId());
            return AgentSubstituteVO.builder()
                    .id(r.getId())
                    .applicantAgentId(r.getApplicantAgentId())
                    .applicantName(app != null ? app.getAgentName() : "")
                    .substituteAgentId(r.getSubstituteAgentId())
                    .substituteName(sub != null ? sub.getAgentName() : "")
                    .substituteType(r.getSubstituteType())
                    .scope(r.getScope())
                    .startTime(r.getStartTime())
                    .endTime(r.getEndTime())
                    .status(r.getStatus())
                    .reason(r.getReason())
                    .confirmedAt(r.getConfirmedAt())
                    .createdAt(r.getCreatedAt())
                    .build();
        }).toList();
    }

    /**
     * 作废/取消替班申请
     *
     * @param id 替班记录主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelSubstitute(Long id) {
        AgentSubstituteRecordEntity entity = substituteRecordMapper.selectById(id);
        if (entity != null) {
            entity.setStatus("CANCELLED");
            entity.setUpdatedAt(LocalDateTime.now());
            substituteRecordMapper.updateById(entity);
            log.info("[AgentService] 替班记录已取消: id={}", id);
        }
    }

    /**
     * 校验姓名合法性 (仅做非空与长度约束，不设任何人员准入名单)
     *
     * @param name 姓名
     */
    private void validateAgentName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("坐席姓名不能为空");
        }
        if (name.trim().length() > 128) {
            throw new IllegalArgumentException("坐席姓名长度不能超过 128");
        }
    }

    /**
     * 实体转 VO
     */
    private AgentVO convertToVO(AgentEntity entity) {
        AuthRoleEnum authRole = AgentRoleEnum.toAuthRole(entity.getRoleCode());
        boolean isSupervisor = authRole == AuthRoleEnum.SUPERVISOR;

        // 当前接听终端必须由显式唯一事实给出，不能通过优先级排序推断。
        List<AgentEndpointBindingEntity> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                        .eq(AgentEndpointBindingEntity::getAgentId, entity.getId())
                        .eq(AgentEndpointBindingEntity::getStatus, "ENABLED")
                        .eq(AgentEndpointBindingEntity::getActive, true));
        if (bindings.size() > 1) {
            throw new IllegalStateException("坐席存在多个当前接听终端: workNo=" + entity.getWorkNo());
        }
        String currentExt = bindings.isEmpty() ? null : bindings.getFirst().getEndpointValue();

        return AgentVO.builder()
                .id(entity.getId())
                .workNo(entity.getWorkNo())
                .agentName(entity.getAgentName())
                .phoneNumber(entity.getPhoneNumber())
                .roleCode(entity.getRoleCode())
                .status(entity.getStatus())
                .isSupervisor(isSupervisor)
                .passwordConfigured(entity.getPasswordHash() != null && !entity.getPasswordHash().isBlank())
                .lastLoginAt(entity.getLastLoginAt())
                .currentExtension(currentExt)
                .boundEndpointType(bindings.isEmpty() ? null : bindings.getFirst().getEndpointType())
                .metadata(entity.getMetadata())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

}
