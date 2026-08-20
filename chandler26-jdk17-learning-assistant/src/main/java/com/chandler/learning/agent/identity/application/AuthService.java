package com.chandler.learning.agent.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.identity.api.AuthRequest;
import com.chandler.learning.agent.identity.api.AuthResponse;
import com.chandler.learning.agent.identity.api.UserProfileResponse;
import com.chandler.learning.agent.identity.api.UserProfileUpdateRequest;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.identity.domain.UserRole;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.infrastructure.LearningUserMapper;
import com.chandler.learning.agent.security.JwtClaims;
import com.chandler.learning.agent.security.JwtTokenService;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 学习助手账户服务，负责注册、登录、JWT 用户解析和个人资料维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PASSWORD_PREFIX = "sha256$";
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-()\\s]{3,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final LearningUserMapper userMapper;
    private final WordbookService wordbookService;
    private final JwtTokenService jwtTokenService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建或保存 {@code register} 相关业务。
     */
    public AuthResponse register(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        LearningUser existing = findByUsername(username);
        if (existing != null) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.USER_ALREADY_EXISTS,
                    "用户名已存在: " + username);
        }

        LocalDateTime now = LocalDateTime.now();
        LearningUser user = new LearningUser();
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setEnabled(true);
        user.setRoleCode(UserRole.USER.getCode());
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        wordbookService.ensureDefaultWordbook(user.getId());
        systemLogService.record(user.getId(), SystemLogType.AUTH, "注册成功", username);
        log.info("用户「{}」完成注册，账号为「{}」", userDisplayNameService.displayName(user), username);
        return createLoginResponse(user);
    }

    /**
     * 处理 {@code login} 相关业务。
     */
    public AuthResponse login(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        LearningUser user = findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            log.debug("登录失败 username={} reason=user_not_found_or_disabled", username);
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            log.debug("登录失败 username={} reason=password_mismatch", username);
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        wordbookService.ensureDefaultWordbook(user.getId());
        systemLogService.record(user.getId(), SystemLogType.AUTH, "登录成功", username);
        log.info("用户「{}」登录成功", userDisplayNameService.displayName(user));
        return createLoginResponse(user);
    }

    /**
     * 处理 {@code me} 相关业务。
     */
    public UserProfileResponse me(String authorization) {
        return toProfile(requireUser(authorization));
    }

    /**
     * 更新 {@code updateProfile} 相关业务。
     */
    public UserProfileResponse updateProfile(String authorization, UserProfileUpdateRequest request) {
        LearningUser user = requireUser(authorization);
        UserProfileUpdateRequest resolvedRequest = request == null ? new UserProfileUpdateRequest() : request;
        boolean changed = false;

        if (resolvedRequest.getNickname() != null) {
            String nickname = resolvedRequest.getNickname().trim();
            user.setNickname(StringUtils.hasText(nickname) ? nickname : user.getUsername());
            changed = true;
        }

        if (StringUtils.hasText(resolvedRequest.getNewPassword())) {
            String newPassword = resolvedRequest.getNewPassword().trim();
            if (newPassword.length() < LearningConstants.Auth.PASSWORD_MIN_LENGTH) {
                throw LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.PASSWORD_TOO_SHORT,
                        "新密码至少 " + LearningConstants.Auth.PASSWORD_MIN_LENGTH + " 位");
            }
            if (!verifyPassword(resolvedRequest.getCurrentPassword(), user.getPasswordHash())) {
                throw LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.PASSWORD_INCORRECT);
            }
            user.setPasswordHash(hashPassword(newPassword));
            changed = true;
        }

        if (resolvedRequest.getPhone() != null) {
            user.setPhone(normalizePhone(resolvedRequest.getPhone()));
            changed = true;
        }

        if (resolvedRequest.getEmail() != null) {
            user.setEmail(normalizeEmail(resolvedRequest.getEmail()));
            changed = true;
        }

        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
            systemLogService.record(user.getId(), SystemLogType.AUTH, "更新账户信息", user.getUsername());
            log.info("用户「{}」更新了账户信息，是否修改密码：{}",
                    userDisplayNameService.displayName(user),
                    StringUtils.hasText(resolvedRequest.getNewPassword()));
        }
        return toProfile(user);
    }

    /**
     * 更新 {@code logout} 相关业务。
     */
    public void logout(String authorization) {
        LearningUser user = currentSecurityUser();
        SecurityContextHolder.clearContext();
        log.info("用户「{}」退出登录", userDisplayNameService.displayName(user));
    }

    /**
     * 处理 {@code requireUser} 相关业务。
     */
    public LearningUser requireUser(String authorization) {
        LearningUser contextUser = currentSecurityUser();
        if (contextUser != null) {
            return contextUser;
        }
        String token = resolveToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw LearningAssistantException.unauthorized(
                    LearningConstants.ErrorCode.AUTH_REQUIRED);
        }
        JwtClaims claims;
        try {
            claims = jwtTokenService.parse(token);
        } catch (RuntimeException ex) {
            throw LearningAssistantException.unauthorized(
                    LearningConstants.ErrorCode.AUTH_EXPIRED);
        }
        LearningUser user = userMapper.selectById(claims.userId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw LearningAssistantException.unauthorized(
                    LearningConstants.ErrorCode.USER_DISABLED);
        }
        return user;
    }

    /**
     * 获取当前系统管理员；所有管理类接口统一从这里完成真实授权。
     */
    public LearningUser requireAdmin(String authorization) {
        LearningUser user = requireUser(authorization);
        if (UserRole.of(user.getRoleCode()) != UserRole.ADMIN) {
            throw LearningAssistantException.of(LearningConstants.ErrorCode.ADMIN_REQUIRED);
        }
        return user;
    }

    /**
     * 判断用户是否为系统管理员。
     */
    public boolean isAdmin(LearningUser user) {
        return user != null && UserRole.of(user.getRoleCode()) == UserRole.ADMIN;
    }

    /**
     * 创建或保存 {@code createLoginResponse} 相关业务。
     */
    private AuthResponse createLoginResponse(LearningUser user) {
        String rawToken = jwtTokenService.createToken(user.getId(), user.getUsername());
        JwtClaims claims = jwtTokenService.parse(rawToken);
        LocalDateTime now = LocalDateTime.now();

        LearningUser update = new LearningUser();
        update.setId(user.getId());
        update.setLastLoginTime(now);
        update.setUpdateTime(now);
        userMapper.updateById(update);

        AuthResponse response = new AuthResponse();
        response.setToken(rawToken);
        response.setExpiredTime(claims.expiredTime());
        response.setUser(toProfile(user));
        log.debug("登录令牌已签发 userId={} username={} expiredTime={}",
                user.getId(),
                user.getUsername(),
                claims.expiredTime());
        return response;
    }

    /**
     * 处理 {@code currentSecurityUser} 相关业务。
     */
    private LearningUser currentSecurityUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LearningUserPrincipal userPrincipal) {
            return userPrincipal.user();
        }
        return null;
    }

    /**
     * 查询 {@code findByUsername} 相关业务。
     */
    private LearningUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getUsername, username)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /**
     * 转换 {@code toProfile} 相关业务。
     */
    private UserProfileResponse toProfile(LearningUser user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setPhoneMasked(maskPhone(user.getPhone()));
        response.setEmailMasked(maskEmail(user.getEmail()));
        UserRole role = UserRole.of(user.getRoleCode());
        response.setRoleCode(role.getCode());
        response.setRoleLabel(role.getLabel());
        return response;
    }

    /**
     * 处理 {@code maskPhone} 相关业务。
     */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String value = phone.trim();
        if (value.length() <= LearningConstants.Auth.PHONE_MASK_THRESHOLD) {
            return LearningConstants.Auth.CONTACT_MASK;
        }
        return value.substring(LearningConstants.ZERO, LearningConstants.Auth.PHONE_MASK_PREFIX_LENGTH)
                + LearningConstants.Auth.CONTACT_MASK
                + value.substring(value.length() - LearningConstants.Auth.PHONE_MASK_SUFFIX_LENGTH);
    }

    /**
     * 处理 {@code maskEmail} 相关业务。
     */
    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        String value = email.trim();
        int atIndex = value.indexOf('@');
        if (atIndex <= LearningConstants.ZERO) {
            return LearningConstants.Auth.CONTACT_MASK;
        }
        String name = value.substring(LearningConstants.ZERO, atIndex);
        String domain = value.substring(atIndex);
        if (name.length() <= LearningConstants.Auth.EMAIL_MASK_VISIBLE_PREFIX_LENGTH) {
            return name.charAt(LearningConstants.ZERO) + LearningConstants.Auth.CONTACT_MASK + domain;
        }
        return name.substring(LearningConstants.ZERO, LearningConstants.Auth.EMAIL_MASK_VISIBLE_PREFIX_LENGTH)
                + LearningConstants.Auth.CONTACT_MASK
                + domain;
    }

    /**
     * 处理 {@code normalizePhone} 相关业务。
     */
    private String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > LearningConstants.Auth.PHONE_MAX_LENGTH || !PHONE_PATTERN.matcher(value).matches()) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.PHONE_INVALID,
                    "手机号码格式不正确");
        }
        return value;
    }

    /**
     * 处理 {@code normalizeEmail} 相关业务。
     */
    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > LearningConstants.Auth.EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.EMAIL_INVALID,
                    "联系邮箱格式不正确");
        }
        return value;
    }

    /**
     * 判断 {@code hashPassword} 相关业务。
     */
    /** 为账户创建与登录校验一致的密码哈希。 */
    public String hashPassword(String password) {
        String salt = randomHex(LearningConstants.Auth.PASSWORD_SALT_BYTES);
        return PASSWORD_PREFIX + salt + "$" + sha256(salt + LearningConstants.Auth.PASSWORD_HASH_SEPARATOR + password);
    }

    /**
     * 处理 {@code randomHex} 相关业务。
     */
    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 处理 {@code verifyPassword} 相关业务。
     */
    private boolean verifyPassword(String password, String passwordHash) {
        if (!StringUtils.hasText(passwordHash) || !passwordHash.startsWith(PASSWORD_PREFIX)) {
            return false;
        }
        String[] parts = passwordHash.split("\\$");
        if (parts.length != LearningConstants.Auth.PASSWORD_HASH_PART_COUNT) {
            return false;
        }
        return MessageDigest.isEqual(parts[LearningConstants.Auth.PASSWORD_DIGEST_PART_INDEX].getBytes(StandardCharsets.UTF_8),
                sha256(parts[LearningConstants.Auth.PASSWORD_SALT_PART_INDEX] + LearningConstants.Auth.PASSWORD_HASH_SEPARATOR + password)
                        .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 处理 {@code sha256} 相关业务。
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.HASH_FAILED,
                    "密码哈希计算失败",
                    ex);
        }
    }

    /**
     * 处理 {@code normalizeUsername} 相关业务。
     */
    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    /**
     * 处理 {@code resolveToken} 相关业务。
     */
    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }
        String value = authorization.trim();
        return value.regionMatches(true, LearningConstants.ZERO,
                LearningConstants.Auth.BEARER_PREFIX, LearningConstants.ZERO, LearningConstants.Auth.BEARER_PREFIX_LENGTH)
                ? value.substring(LearningConstants.Auth.BEARER_PREFIX_LENGTH).trim()
                : value;
    }
}
