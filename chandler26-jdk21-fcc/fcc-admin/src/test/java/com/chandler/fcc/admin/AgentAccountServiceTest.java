package com.chandler.fcc.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.agent.application.AgentEndpointService;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.AgentCreateReq;
import com.chandler.fcc.admin.model.dto.AgentGroupCreateReq;
import com.chandler.fcc.admin.model.dto.AgentGroupMemberReq;
import com.chandler.fcc.admin.model.dto.AgentQueryReq;
import com.chandler.fcc.admin.model.dto.AgentSubstituteReq;
import com.chandler.fcc.admin.model.vo.AccountCredentialVO;
import com.chandler.fcc.admin.model.vo.AgentBindingVO;
import com.chandler.fcc.admin.model.vo.AgentGroupMemberVO;
import com.chandler.fcc.admin.model.vo.AgentGroupVO;
import com.chandler.fcc.admin.model.vo.AgentSubstituteVO;
import com.chandler.fcc.admin.model.vo.AgentVO;
import com.chandler.fcc.admin.service.AgentService;
import com.chandler.fcc.common.util.PasswordHasher;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 坐席账号、组织协同与口令治理集成测试
 * <p>
 * 坐席档案与登录账号统一以 fcc_agent 表为基准：系统不设任何人员准入名单，
 * 任意合法工号均可创建；口令以 PBKDF2 派生串落库，未指定时由系统生成一次性随机口令。
 * </p>
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccAdminApplication.class)
@ActiveProfiles("local")
@Transactional
public class AgentAccountServiceTest extends EphemeralSipKeyTest {

    private static final String TEST_WORK_NO = "T90001";
    private static final String TEST_WORK_NO_MEMBER = "T90002";
    private static final String TEST_WORK_NO_GROUP = "T90003";
    private static final String TEST_WORK_NO_BINDING = "T90004";
    private static final String TEST_WORK_NO_APPLICANT = "T90005";
    private static final String TEST_WORK_NO_SUBSTITUTE = "T90006";

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private AgentEndpointService agentEndpointService;

    @BeforeEach
    void cleanupPreExistingAgents() {
        agentMapper.delete(new LambdaQueryWrapper<AgentEntity>()
                .in(AgentEntity::getWorkNo, List.of(TEST_WORK_NO, TEST_WORK_NO_MEMBER, TEST_WORK_NO_GROUP,
                        TEST_WORK_NO_BINDING, TEST_WORK_NO_APPLICANT, TEST_WORK_NO_SUBSTITUTE)));
    }

    /**
     * 测试坐席创建：任意合法姓名均可录入，且口令以派生串落库
     */
    @Test
    @DisplayName("测试坐席录入不设人员名单限制，口令以 PBKDF2 派生串落库")
    void testCreateAgentWithoutRosterRestriction() {
        AccountCredentialVO supervisorCredential = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO)
                .agentName("钱丁君")
                .phoneNumber("13800000001")
                .roleCode("AGENT_ADMIN")
                .build());
        assertNotNull(supervisorCredential.getId());
        assertNotNull(supervisorCredential.getInitialPassword(), "未指定口令时必须生成一次性初始口令");
        assertNull(supervisorCredential.getExtensionSecret(), "管理开通响应不能暴露 SIP 注册口令");

        AgentVO supervisor = agentService.getAgentById(supervisorCredential.getId());
        assertNotNull(supervisor);
        assertEquals("钱丁君", supervisor.getAgentName());
        assertEquals(TEST_WORK_NO, supervisor.getWorkNo());
        assertEquals("AGENT_ADMIN", supervisor.getRoleCode());
        assertTrue(supervisor.getIsSupervisor(), "AGENT_ADMIN 角色必须被识别为主管席位");
        assertTrue(supervisor.getPasswordConfigured(), "创建后必须已配置登录口令");

        // 库内不应出现明文口令，只保留 PBKDF2 派生串
        AgentEntity stored = agentMapper.selectById(supervisorCredential.getId());
        assertNotNull(stored.getPasswordHash());
        assertTrue(PasswordHasher.looksLikeHash(stored.getPasswordHash()),
                "口令必须为 pbkdf2-sha256 派生串格式: " + stored.getPasswordHash());
        assertNotEquals(supervisorCredential.getInitialPassword(), stored.getPasswordHash());
        assertTrue(PasswordHasher.verify(supervisorCredential.getInitialPassword(), stored.getPasswordHash()),
                "生成的初始口令必须能通过派生串校验");

        // 未指定角色的坐席默认回落为普通坐席
        AccountCredentialVO memberCredential = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_MEMBER)
                .agentName("任意新成员")
                .phoneNumber("13800000002")
                .build());
        AgentVO member = agentService.getAgentById(memberCredential.getId());
        assertEquals("AGENT_MEMBER", member.getRoleCode());
        assertFalse(member.getIsSupervisor(), "普通坐席不应被识别为主管席位");
    }

    /**
     * 测试重复工号被拒绝
     */
    @Test
    @DisplayName("测试坐席工号唯一性约束")
    void testDuplicateWorkNoRejected() {
        agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_MEMBER)
                .agentName("重复工号测试")
                .build());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                agentService.createAgent(AgentCreateReq.builder()
                        .workNo(TEST_WORK_NO_MEMBER)
                        .agentName("另一个人")
                        .build()));
        assertTrue(ex.getMessage().contains("已存在"), "错误提示应说明工号已存在: " + ex.getMessage());
    }

    /**
     * 测试管理员重置坐席口令
     */
    @Test
    @DisplayName("测试重置坐席口令后旧口令失效、新口令生效")
    void testResetAgentPassword() {
        AccountCredentialVO created = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO)
                .agentName("口令重置测试")
                .build());
        String originalPlain = created.getInitialPassword();
        assertNotNull(originalPlain);

        AccountCredentialVO reset = agentService.resetPassword(created.getId(), null);
        assertNotNull(reset.getInitialPassword(), "留空重置必须返回生成的一次性口令");

        AgentEntity stored = agentMapper.selectById(created.getId());
        assertFalse(PasswordHasher.verify(originalPlain, stored.getPasswordHash()), "旧口令必须失效");
        assertTrue(PasswordHasher.verify(reset.getInitialPassword(), stored.getPasswordHash()), "新口令必须生效");

        // 显式指定口令时不回传明文
        AccountCredentialVO explicit = agentService.resetPassword(created.getId(), "Explicit@2026");
        assertNull(explicit.getInitialPassword());
        assertTrue(PasswordHasher.verify("Explicit@2026", agentMapper.selectById(created.getId()).getPasswordHash()));
    }

    /**
     * 测试分页检索
     */
    @Test
    @DisplayName("测试坐席多条件分页检索")
    void testQueryAgents() {
        agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_MEMBER)
                .agentName("检索测试成员")
                .phoneNumber("13800000007")
                .build());

        PageResult<AgentVO> page = agentService.queryAgents(AgentQueryReq.builder()
                .workNo(TEST_WORK_NO_MEMBER)
                .pageNum(1)
                .pageSize(10)
                .build());
        assertNotNull(page);
        assertTrue(page.getTotal() >= 1);
        assertEquals(TEST_WORK_NO_MEMBER, page.getList().getFirst().getWorkNo());
    }

    /**
     * 测试技能组创建与成员分配
     */
    @Test
    @DisplayName("测试技能组创建与坐席分配组员")
    void testAgentGroupAndMemberBinding() {
        AccountCredentialVO credential = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_GROUP)
                .agentName("技能组测试成员")
                .build());

        Long groupId = agentService.createGroup(AgentGroupCreateReq.builder()
                .groupCode("SKILL_VIP_TEST")
                .groupName("VIP专家客服组")
                .groupType("SKILL")
                .routingStrategy("LONGEST_IDLE")
                .build());
        assertNotNull(groupId);

        agentService.addMemberToGroup(AgentGroupMemberReq.builder()
                .groupId(groupId)
                .agentId(credential.getId())
                .memberRole("MEMBER")
                .priority(1)
                .build());

        List<AgentGroupVO> groups = agentService.listGroups();
        assertTrue(groups.stream().anyMatch(g -> g.getId().equals(groupId) && g.getMemberCount() == 1));

        agentService.removeMemberFromGroup(groupId, credential.getId());
        groups = agentService.listGroups();
        assertTrue(groups.stream().anyMatch(g -> g.getId().equals(groupId) && g.getMemberCount() == 0));
    }

    /**
     * 验证父组织查询会一次返回子树坐席，并按坐席主键去重。
     */
    @Test
    @DisplayName("父组织成员列表包含子节点坐席并去重分页")
    void testListSubtreeMembers() {
        AccountCredentialVO directAgent = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_GROUP)
                .agentName("父节点直属坐席")
                .build());
        AccountCredentialVO childAgent = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_MEMBER)
                .agentName("子节点坐席")
                .build());

        Long parentId = agentService.createGroup(AgentGroupCreateReq.builder()
                .groupCode("ORG_SUBTREE_PARENT_TEST")
                .groupName("子树查询父节点")
                .groupType("CENTER")
                .build());
        Long childId = agentService.createGroup(AgentGroupCreateReq.builder()
                .groupCode("ORG_SUBTREE_CHILD_TEST")
                .groupName("子树查询子节点")
                .groupType("SKILL")
                .parentId(parentId)
                .build());

        agentService.addMemberToGroup(AgentGroupMemberReq.builder()
                .groupId(parentId)
                .agentId(directAgent.getId())
                .memberRole("MEMBER")
                .priority(0)
                .build());
        agentService.addMemberToGroup(AgentGroupMemberReq.builder()
                .groupId(childId)
                .agentId(directAgent.getId())
                .memberRole("MEMBER")
                .priority(2)
                .build());
        agentService.addMemberToGroup(AgentGroupMemberReq.builder()
                .groupId(childId)
                .agentId(childAgent.getId())
                .memberRole("MEMBER")
                .priority(1)
                .build());

        PageResult<AgentGroupMemberVO> page = agentService.listGroupMembers(
                parentId,
                1,
                10,
                null
        );
        assertEquals(2L, page.getTotal());
        assertEquals(2, page.getList().size());
        assertEquals(
                String.valueOf(parentId),
                page.getList().stream()
                        .filter(member -> member.getAgentId().equals(String.valueOf(directAgent.getId())))
                        .findFirst()
                        .orElseThrow()
                        .getGroupId(),
                "同一坐席重复入组时应优先展示距离所选节点最近的成员关系"
        );
        assertTrue(page.getList().stream().anyMatch(member ->
                member.getAgentId().equals(String.valueOf(childAgent.getId()))
                        && member.getGroupId().equals(String.valueOf(childId))));

        PageResult<AgentGroupMemberVO> searched = agentService.listGroupMembers(
                parentId,
                1,
                10,
                "子节点坐席"
        );
        assertEquals(1L, searched.getTotal());
        assertEquals(String.valueOf(childAgent.getId()), searched.getList().getFirst().getAgentId());
    }

    /**
     * 测试坐席终端绑定
     */
    @Test
    @DisplayName("新坐席只初始化唯一 WebRTC 当前终端")
    void testDefaultEndpointSelection() {
        AccountCredentialVO credential = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_BINDING)
                .agentName("终端绑定测试成员")
                .build());

        List<AgentBindingVO> bindings = agentService.listBindingsByAgentId(credential.getId());
        assertEquals(1, bindings.size());
        assertEquals("WEBRTC", agentEndpointService.get(TEST_WORK_NO_BINDING).getActiveEndpointType());
        assertEquals(TEST_WORK_NO_BINDING, agentEndpointService.get(TEST_WORK_NO_BINDING).getActiveEndpointValue());
        assertTrue(agentEndpointService.get(TEST_WORK_NO_BINDING).getAvailableSipExtensions().isEmpty());
    }

    /**
     * 测试坐席替班与夜班代接流转
     */
    @Test
    @DisplayName("测试坐席夜班/请假替班申请与取消流转")
    void testSubstituteFlow() {
        AccountCredentialVO applicant = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_APPLICANT)
                .agentName("替班申请人")
                .build());

        AccountCredentialVO substitute = agentService.createAgent(AgentCreateReq.builder()
                .workNo(TEST_WORK_NO_SUBSTITUTE)
                .agentName("替班承接人")
                .build());

        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = start.plusHours(8);

        Long recordId = agentService.applySubstitute(AgentSubstituteReq.builder()
                .applicantAgentId(applicant.getId())
                .substituteAgentId(substitute.getId())
                .substituteType("PP")
                .scope("NIGHT_OFF")
                .startTime(start)
                .endTime(end)
                .reason("夜班值班替班")
                .build());
        assertNotNull(recordId);

        List<AgentSubstituteVO> list = agentService.listSubstitutes();
        AgentSubstituteVO vo = list.stream().filter(r -> r.getId().equals(recordId)).findFirst().orElse(null);
        assertNotNull(vo);
        assertEquals("替班申请人", vo.getApplicantName());
        assertEquals("替班承接人", vo.getSubstituteName());
        assertEquals("CONFIRMED", vo.getStatus());

        agentService.cancelSubstitute(recordId);
        list = agentService.listSubstitutes();
        AgentSubstituteVO cancelled = list.stream().filter(r -> r.getId().equals(recordId)).findFirst().orElse(null);
        assertNotNull(cancelled);
        assertEquals("CANCELLED", cancelled.getStatus());
    }
}
