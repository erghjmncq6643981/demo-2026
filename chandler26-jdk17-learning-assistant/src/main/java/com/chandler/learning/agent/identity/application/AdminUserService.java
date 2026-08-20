package com.chandler.learning.agent.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.identity.api.AdminUserPageResponse;
import com.chandler.learning.agent.identity.api.AdminUserResetPasswordRequest;
import com.chandler.learning.agent.identity.api.AdminUserResponse;
import com.chandler.learning.agent.identity.api.AdminUserSaveRequest;
import com.chandler.learning.agent.identity.api.AdminUserUpdateRequest;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.identity.domain.UserRole;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.infrastructure.LearningUserMapper;
import com.chandler.learning.agent.learning.application.LearningPlanAccessService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 系统用户中心服务。
 * <p>
 * 用户注销采用逻辑删除，保留学习计划、复习记录和审计日志的历史关联。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LearningUserMapper userMapper;
    private final WordbookService wordbookService;
    private final LearningPlanAccessService learningPlanAccessService;
    private final AuthService authService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /** 查询用户中心分页列表。 */
    public AdminUserPageResponse page(String keyword, String roleCode, Boolean enabled,
                                      LocalDateTime registeredFrom, LocalDateTime registeredTo,
                                      LocalDateTime lastLoginFrom, LocalDateTime lastLoginTo,
                                      Integer page, Integer pageSize) {
        int resolvedPage = page == null ? DEFAULT_PAGE : Math.max(DEFAULT_PAGE, page);
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE
                : Math.max(DEFAULT_PAGE, Math.min(pageSize, MAX_PAGE_SIZE));
        LambdaQueryWrapper<LearningUser> wrapper = buildQuery(keyword, roleCode, enabled,
                registeredFrom, registeredTo, lastLoginFrom, lastLoginTo);
        long total = userMapper.selectCount(wrapper);
        long offset = (long) (resolvedPage - 1) * resolvedPageSize;
        List<LearningUser> users = userMapper.selectList(buildQuery(keyword, roleCode, enabled,
                        registeredFrom, registeredTo, lastLoginFrom, lastLoginTo)
                .orderByDesc(LearningUser::getCreateTime)
                .last("LIMIT " + offset + ", " + resolvedPageSize));

        AdminUserPageResponse response = new AdminUserPageResponse();
        response.setItems(toResponses(users));
        response.setTotal(total);
        response.setPage(resolvedPage);
        response.setPageSize(resolvedPageSize);
        return response;
    }

    /** 查询一个未注销用户。 */
    public AdminUserResponse detail(Long userId) {
        return toResponses(List.of(requireActiveUser(userId))).get(0);
    }

    /** 新建用户，并创建默认个人单词本。 */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserResponse create(LearningUser operator, AdminUserSaveRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (userMapper.selectCount(new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getUsername, username)) > 0) {
            throw LearningAssistantException.of(LearningConstants.ErrorCode.USER_ALREADY_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now();
        LearningUser user = new LearningUser();
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPasswordHash(authService.hashPassword(request.getPassword()));
        user.setRoleCode(resolveRole(request.getRoleCode()).getCode());
        user.setEnabled(request.getEnabled() == null || request.getEnabled());
        user.setCreateBy(operator.getId());
        user.setUpdateBy(operator.getId());
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleted(false);
        user.setVersion(LearningConstants.Audit.INITIAL_VERSION);
        userMapper.insert(user);
        wordbookService.ensureDefaultWordbook(user.getId());
        writeAudit(operator, "新增用户", user);
        log.info("系统管理员「{}」新增用户「{}」，角色「{}」",
                userDisplayNameService.displayName(operator), user.getUsername(), UserRole.of(user.getRoleCode()).getLabel());
        return detail(user.getId());
    }

    /** 修改昵称、角色和账户状态；用户名不可在后台修改。 */
    @Transactional(rollbackFor = Exception.class)
    public AdminUserResponse update(LearningUser operator, Long userId, AdminUserUpdateRequest request) {
        LearningUser user = requireActiveUser(userId);
        UserRole targetRole = request.getRoleCode() == null ? UserRole.of(user.getRoleCode()) : resolveRole(request.getRoleCode());
        boolean targetEnabled = request.getEnabled() == null || request.getEnabled();
        protectLastEnabledAdmin(user, targetRole, targetEnabled);

        if (request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            user.setNickname(StringUtils.hasText(nickname) ? nickname : user.getUsername());
        }
        user.setRoleCode(targetRole.getCode());
        user.setEnabled(targetEnabled);
        user.setUpdateBy(operator.getId());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        writeAudit(operator, "修改用户", user);
        log.info("系统管理员「{}」修改用户「{}」，角色「{}」，状态「{}」",
                userDisplayNameService.displayName(operator), user.getUsername(), targetRole.getLabel(), targetEnabled ? "启用" : "停用");
        return detail(userId);
    }

    /** 管理员重置用户密码，不返回密码原文或哈希。 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(LearningUser operator, Long userId, AdminUserResetPasswordRequest request) {
        LearningUser user = requireActiveUser(userId);
        user.setPasswordHash(authService.hashPassword(request.getPassword()));
        user.setUpdateBy(operator.getId());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        writeAudit(operator, "重置用户密码", user);
        log.info("系统管理员「{}」重置了用户「{}」的密码", userDisplayNameService.displayName(operator), user.getUsername());
    }

    /** 注销用户而非物理删除，以保留学习历史和审计链路。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(LearningUser operator, Long userId) {
        if (operator.getId().equals(userId)) {
            throw LearningAssistantException.of(LearningConstants.ErrorCode.ADMIN_SELF_OPERATION_FORBIDDEN);
        }
        LearningUser user = requireActiveUser(userId);
        protectLastEnabledAdmin(user, UserRole.of(user.getRoleCode()), false);
        user.setEnabled(false);
        user.setUpdateBy(operator.getId());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        userMapper.deleteById(userId);
        writeAudit(operator, "注销用户", user);
        log.info("系统管理员「{}」注销了用户「{}」", userDisplayNameService.displayName(operator), user.getUsername());
    }

    private LambdaQueryWrapper<LearningUser> buildQuery(String keyword, String roleCode, Boolean enabled,
                                                          LocalDateTime registeredFrom, LocalDateTime registeredTo,
                                                          LocalDateTime lastLoginFrom, LocalDateTime lastLoginTo) {
        LambdaQueryWrapper<LearningUser> wrapper = new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getDeleted, false);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query.like(LearningUser::getUsername, value)
                    .or().like(LearningUser::getNickname, value));
        }
        if (StringUtils.hasText(roleCode)) {
            wrapper.eq(LearningUser::getRoleCode, resolveRole(roleCode).getCode());
        }
        if (enabled != null) {
            wrapper.eq(LearningUser::getEnabled, enabled);
        }
        if (registeredFrom != null) wrapper.ge(LearningUser::getCreateTime, registeredFrom);
        if (registeredTo != null) wrapper.le(LearningUser::getCreateTime, registeredTo);
        if (lastLoginFrom != null) wrapper.ge(LearningUser::getLastLoginTime, lastLoginFrom);
        if (lastLoginTo != null) wrapper.le(LearningUser::getLastLoginTime, lastLoginTo);
        return wrapper;
    }

    private List<AdminUserResponse> toResponses(List<LearningUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(LearningUser::getId).toList();
        Map<Long, Integer> wordbookCounts = wordbookService.countByUserIds(userIds);
        Map<Long, Integer> planCounts = learningPlanAccessService.countByUserIds(userIds);
        return users.stream().map(user -> {
            UserRole role = UserRole.of(user.getRoleCode());
            AdminUserResponse response = new AdminUserResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setNickname(user.getNickname());
            response.setRoleCode(role.getCode());
            response.setRoleLabel(role.getLabel());
            response.setEnabled(user.getEnabled());
            response.setWordbookCount(wordbookCounts.getOrDefault(user.getId(), 0));
            response.setLearningPlanCount(planCounts.getOrDefault(user.getId(), 0));
            response.setLastLoginTime(user.getLastLoginTime());
            response.setCreateTime(user.getCreateTime());
            return response;
        }).toList();
    }

    private LearningUser requireActiveUser(Long userId) {
        LearningUser user = userMapper.selectOne(new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getId, userId)
                .eq(LearningUser::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (user == null) {
            throw LearningAssistantException.of(LearningConstants.ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserRole resolveRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return UserRole.USER;
        }
        for (UserRole role : UserRole.values()) {
            if (role.getCode().equalsIgnoreCase(roleCode.trim())) {
                return role;
            }
        }
        throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.SYSTEM_UNEXPECTED, "用户角色不正确");
    }

    private void protectLastEnabledAdmin(LearningUser user, UserRole targetRole, boolean targetEnabled) {
        boolean removesEnabledAdmin = UserRole.of(user.getRoleCode()) == UserRole.ADMIN
                && Boolean.TRUE.equals(user.getEnabled())
                && (targetRole != UserRole.ADMIN || !targetEnabled);
        if (!removesEnabledAdmin) {
            return;
        }
        long enabledAdminCount = userMapper.selectCount(new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getRoleCode, UserRole.ADMIN.getCode())
                .eq(LearningUser::getEnabled, true)
                .eq(LearningUser::getDeleted, false));
        if (enabledAdminCount <= 1) {
            throw LearningAssistantException.of(LearningConstants.ErrorCode.LAST_ADMIN_REQUIRED);
        }
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void writeAudit(LearningUser operator, String action, LearningUser target) {
        systemLogService.record(operator.getId(), SystemLogType.AUTH,
                "用户管理：" + action,
                "目标用户：" + target.getUsername() + "（ID：" + target.getId() + "）");
    }
}
