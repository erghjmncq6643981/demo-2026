package com.chandler.fcc.admin.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AdminUserEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEndpointBindingEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminUserMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentEndpointBindingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.model.dto.ChangePasswordReq;
import com.chandler.fcc.admin.model.dto.LoginReq;
import com.chandler.fcc.admin.model.enums.AgentRoleEnum;
import com.chandler.fcc.admin.model.enums.AuthRoleEnum;
import com.chandler.fcc.admin.model.vo.LoginRespVO;
import com.chandler.fcc.admin.model.vo.UserInfoVO;
import com.chandler.fcc.common.util.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 统一认证与权限治理服务
 * <p>
 * 账号与口令全部以数据库为唯一基准，代码中不内置任何账号、默认口令或人员名单：
 * </p>
 * <ul>
 *   <li><b>控制台账号</b>：{@code fcc_admin_user} 表，按 {@code username} 匹配；</li>
 *   <li><b>坐席账号</b>：{@code fcc_agent} 表，按 {@code work_no} 匹配；</li>
 *   <li>口令统一以 PBKDF2 派生串存储，校验采用恒定时间比较；</li>
 *   <li>账号不存在、已停用、未设置口令、口令错误一律返回同一提示，避免账号枚举。</li>
 * </ul>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 登录失败统一提示 (不区分账号不存在与口令错误)
     */
    private static final String LOGIN_FAILED_MESSAGE = "账号或口令错误";

    /**
     * 会话主体类型：控制台账号
     */
    public static final String SUBJECT_CONSOLE = "CONSOLE";

    /**
     * 会话主体类型：坐席账号
     */
    public static final String SUBJECT_AGENT = "AGENT";

    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_ENABLED = "ENABLED";

    private final AdminUserMapper adminUserMapper;
    private final AgentMapper agentMapper;
    private final AgentEndpointBindingMapper bindingMapper;

    /**
     * 统一登录入口
     * <p>
     * 先按控制台账号匹配，未命中再按坐席工号匹配。因此坐席登录时直接输入自己的工号即可，
     * 无需在请求中声明身份类型。
     * </p>
     *
     * @param req 登录入参
     * @return 登录结果令牌 VO
     */
    public LoginRespVO login(LoginReq req) {
        String account = req.getUsername().trim();
        // 口令不做 trim：首尾空格属于口令内容的一部分
        String password = req.getPassword();

        AdminUserEntity consoleUser = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getUsername, account)
                .isNull(AdminUserEntity::getDeletedAt));
        if (consoleUser != null) {
            return loginConsoleUser(consoleUser, password);
        }

        AgentEntity agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkNo, account)
                .isNull(AgentEntity::getDeletedAt));
        if (agent != null) {
            return loginAgent(agent, password);
        }

        log.warn("🔐 [Sa-Token] 登录失败: 账号 {} 在控制台账号表与坐席表中均不存在", account);
        throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
    }

    /**
     * 控制台账号登录
     */
    private LoginRespVO loginConsoleUser(AdminUserEntity user, String password) {
        if (!STATUS_ENABLED.equalsIgnoreCase(normalizeStatus(user.getStatus()))) {
            log.warn("🔐 [Sa-Token] 登录失败: 控制台账号 {} 已停用", user.getUsername());
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }
        if (!PasswordHasher.verify(password, user.getPasswordHash())) {
            log.warn("🔐 [Sa-Token] 登录失败: 控制台账号 {} 口令校验未通过", user.getUsername());
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }

        AuthRoleEnum role = AuthRoleEnum.ofCode(user.getRoleCode());
        String loginId = user.getUsername();

        StpUtil.login(loginId);
        SaSession session = StpUtil.getSession();
        session.set("accountType", SUBJECT_CONSOLE);
        session.set("subjectId", user.getId());
        session.set("realName", user.getRealName());
        session.set("role", role.getCode());
        session.set("roles", role.getRoles());
        session.set("permissions", role.getPermissions());

        adminUserMapper.updateById(AdminUserEntity.builder()
                .id(user.getId())
                .lastLoginAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        log.info("🔐 [Sa-Token] 控制台账号 {} ({}) 登录成功, role={}", loginId, user.getRealName(), role.getCode());

        return LoginRespVO.builder()
                .tokenValue(StpUtil.getTokenValue())
                .tokenName(StpUtil.getTokenName())
                .loginId(loginId)
                .realName(user.getRealName())
                .role(role.getCode())
                .permissions(role.getPermissions())
                .accountType(SUBJECT_CONSOLE)
                .build();
    }

    /**
     * 坐席账号登录
     */
    private LoginRespVO loginAgent(AgentEntity agent, String password) {
        if (!STATUS_ENABLED.equalsIgnoreCase(normalizeStatus(agent.getStatus()))) {
            log.warn("🔐 [Sa-Token] 登录失败: 坐席 {} 已停用", agent.getWorkNo());
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }
        if (agent.getPasswordHash() == null || agent.getPasswordHash().isBlank()) {
            log.warn("🔐 [Sa-Token] 登录失败: 坐席 {} 尚未设置登录口令", agent.getWorkNo());
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }
        if (!PasswordHasher.verify(password, agent.getPasswordHash())) {
            log.warn("🔐 [Sa-Token] 登录失败: 坐席 {} 口令校验未通过", agent.getWorkNo());
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }

        AuthRoleEnum role = AgentRoleEnum.toAuthRole(agent.getRoleCode());
        String loginId = agent.getWorkNo();

        EndpointChoice endpoint = resolveEndpoint(agent);

        StpUtil.login(loginId);
        SaSession session = StpUtil.getSession();
        session.set("accountType", SUBJECT_AGENT);
        session.set("subjectId", agent.getId());
        session.set("realName", resolveAgentDisplayName(agent));
        session.set("role", role.getCode());
        session.set("roles", role.getRoles());
        session.set("permissions", role.getPermissions());
        session.set("extension", endpoint.extension());
        session.set("endpointType", endpoint.endpointType());

        agentMapper.updateById(AgentEntity.builder()
                .id(agent.getId())
                .lastLoginAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        log.info("🎧 [Sa-Token] 坐席 {} ({}) 登录成功, role={}, extension={}, endpointType={}",
                loginId, resolveAgentDisplayName(agent), role.getCode(), endpoint.extension(), endpoint.endpointType());

        return LoginRespVO.builder()
                .tokenValue(StpUtil.getTokenValue())
                .tokenName(StpUtil.getTokenName())
                .loginId(loginId)
                .realName(resolveAgentDisplayName(agent))
                .role(role.getCode())
                .permissions(role.getPermissions())
                .accountType(SUBJECT_AGENT)
                .extension(endpoint.extension())
                .endpointType(endpoint.endpointType())
                .build();
    }

    /**
     * 获取当前登录态详情
     *
     * @return 用户信息 VO
     */
    @SuppressWarnings("unchecked")
    public UserInfoVO getLoginUserInfo() {
        if (!StpUtil.isLogin()) {
            throw new IllegalArgumentException("当前未登录或会话已过期");
        }

        SaSession session = StpUtil.getSession();
        List<String> roles = (List<String>) session.get("roles");
        List<String> permissions = (List<String>) session.get("permissions");

        return UserInfoVO.builder()
                .loginId(StpUtil.getLoginIdAsString())
                .realName(session.getString("realName"))
                .role(session.getString("role"))
                .roles(roles != null ? roles : Collections.emptyList())
                .permissions(permissions != null ? permissions : Collections.emptyList())
                .accountType(session.getString("accountType"))
                .extension(session.getString("extension"))
                .endpointType(session.getString("endpointType"))
                .build();
    }

    /**
     * 当前登录用户自助修改口令
     * <p>
     * 控制台账号与坐席账号共用同一入口：先校验原口令，再写入新的派生串并记录设置时间。
     * </p>
     *
     * @param req 修改口令入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReq req) {
        if (!StpUtil.isLogin()) {
            throw new IllegalArgumentException("当前未登录或会话已过期");
        }
        String loginId = StpUtil.getLoginIdAsString();
        SaSession session = StpUtil.getSession();
        String accountType = session.getString("accountType");

        if (SUBJECT_CONSOLE.equals(accountType)) {
            changeConsolePassword(loginId, req);
        } else if (SUBJECT_AGENT.equals(accountType)) {
            changeAgentPassword(loginId, req);
        } else {
            // 兼容历史会话：按账号逐表定位主体
            if (!tryChangeConsolePassword(loginId, req)) {
                changeAgentPassword(loginId, req);
            }
        }
    }

    /**
     * 注销当前登录会话
     */
    public void logout() {
        if (StpUtil.isLogin()) {
            String loginId = StpUtil.getLoginIdAsString();
            StpUtil.logout();
            log.info("🚪 [Sa-Token] 用户 {} 已成功登出", loginId);
        }
    }

    /**
     * 控制台账号改口令
     */
    private void changeConsolePassword(String username, ChangePasswordReq req) {
        AdminUserEntity user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getUsername, username)
                .isNull(AdminUserEntity::getDeletedAt));
        if (user == null) {
            throw new IllegalArgumentException("当前登录账号不存在");
        }
        if (!PasswordHasher.verify(req.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("原口令不正确");
        }
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordHash(PasswordHasher.hash(req.getNewPassword()));
        user.setPasswordUpdatedAt(now);
        user.setUpdatedAt(now);
        adminUserMapper.updateById(user);
        log.info("🔑 [Sa-Token] 控制台账号 {} 修改口令成功", username);
    }

    /**
     * 尝试按控制台账号改口令，主体不存在时返回 false
     */
    private boolean tryChangeConsolePassword(String username, ChangePasswordReq req) {
        Long exists = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getUsername, username)
                .isNull(AdminUserEntity::getDeletedAt));
        if (exists == null || exists == 0) {
            return false;
        }
        changeConsolePassword(username, req);
        return true;
    }

    /**
     * 坐席账号改口令
     */
    private void changeAgentPassword(String workNo, ChangePasswordReq req) {
        AgentEntity agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getWorkNo, workNo)
                .isNull(AgentEntity::getDeletedAt));
        if (agent == null) {
            throw new IllegalArgumentException("当前登录账号不存在");
        }
        if (!PasswordHasher.verify(req.getOldPassword(), agent.getPasswordHash())) {
            throw new IllegalArgumentException("原口令不正确");
        }
        LocalDateTime now = LocalDateTime.now();
        agent.setPasswordHash(PasswordHasher.hash(req.getNewPassword()));
        agent.setPasswordUpdatedAt(now);
        agent.setUpdatedAt(now);
        agentMapper.updateById(agent);
        log.info("🔑 [Sa-Token] 坐席 {} 修改口令成功", workNo);
    }

    /**
     * 解析坐席在数据库中的接听终端配置
     * <p>
     * 全部取值来自 fcc_agent 与 fcc_agent_endpoint_binding，不再回落到任何硬编码分机号。
     * </p>
     *
     * @param agent 坐席实体
     * @return 分机号与终端类型
     */
    private EndpointChoice resolveEndpoint(AgentEntity agent) {
        List<AgentEndpointBindingEntity> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<AgentEndpointBindingEntity>()
                        .eq(AgentEndpointBindingEntity::getAgentId, agent.getId())
                        .eq(AgentEndpointBindingEntity::getStatus, STATUS_ENABLED)
                        .orderByAsc(AgentEndpointBindingEntity::getPriority));

        if (bindings.isEmpty()) {
            // 未配置显式绑定时，以坐席工号作为默认 WebRTC 软话机分机
            return new EndpointChoice(agent.getWorkNo(), "WEBRTC");
        }

        AgentEndpointBindingEntity binding = bindings.getFirst();
        String endpointType = (binding.getEndpointType() != null && !binding.getEndpointType().isBlank())
                ? binding.getEndpointType().toUpperCase()
                : "WEBRTC";

        switch (endpointType) {
            case "SIP" -> {
                String extension = firstNonBlank(agent.getCurrentExtension(), binding.getEndpointValue(), agent.getWorkNo());
                return new EndpointChoice(extension, "SIP");
            }
            case "MOBILE" -> {
                String mobile = firstNonBlank(binding.getEndpointValue(), agent.getPhoneNumber());
                return new EndpointChoice(mobile, "MOBILE");
            }
            default -> {
                String extension = firstNonBlank(binding.getEndpointValue(), agent.getCurrentExtension(), agent.getWorkNo());
                return new EndpointChoice(extension, "WEBRTC");
            }
        }
    }

    /**
     * 解析坐席显示姓名
     */
    private String resolveAgentDisplayName(AgentEntity agent) {
        if (agent.getAgentName() != null && !agent.getAgentName().isBlank()) {
            return agent.getAgentName();
        }
        return "坐席" + agent.getWorkNo();
    }

    /**
     * 返回第一个非空白取值
     */
    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 归一化状态取值
     */
    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? STATUS_ENABLED : status.trim().toUpperCase();
    }

    /**
     * 坐席接听终端选择结果
     *
     * @param extension    分机号或随行手机号
     * @param endpointType 终端类型 (WEBRTC / SIP / MOBILE)
     */
    private record EndpointChoice(String extension, String endpointType) {
    }
}
