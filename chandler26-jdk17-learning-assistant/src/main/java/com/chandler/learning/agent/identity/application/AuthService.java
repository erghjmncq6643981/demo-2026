package com.chandler.learning.agent.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.identity.api.request.AuthRequest;
import com.chandler.learning.agent.identity.api.response.AuthResponse;
import com.chandler.learning.agent.identity.api.response.UserProfileResponse;
import com.chandler.learning.agent.identity.api.request.UserProfileUpdateRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.identity.domain.enums.UserRole;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.infrastructure.mapper.LearningUserMapper;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.JwtClaims;
import com.chandler.learning.agent.security.JwtTokenService;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.security.constant.AuthConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CurrentUserContext currentUserContext;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 注册学习账户。 */
    public AuthResponse register(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        LearningUser existing = findByUsername(username);
        if (existing != null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.USER_ALREADY_EXISTS,
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

    /** 校验账户凭据并登录。 */
    public AuthResponse login(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        LearningUser user = findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            log.debug("登录失败 username={} reason=user_not_found_or_disabled", username);
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            log.debug("登录失败 username={} reason=password_mismatch", username);
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        wordbookService.ensureDefaultWordbook(user.getId());
        systemLogService.record(user.getId(), SystemLogType.AUTH, "登录成功", username);
        log.info("用户「{}」登录成功", userDisplayNameService.displayName(user));
        return createLoginResponse(user);
    }

    /** 查询当前登录用户资料。 */
    public UserProfileResponse me() {
        return toProfile(currentUserContext.requireUser());
    }

    /** 更新当前用户昵称、联系方式或密码。 */
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        UserProfileUpdateRequest resolvedRequest = request == null ? new UserProfileUpdateRequest() : request;
        boolean changed = false;

        if (resolvedRequest.getNickname() != null) {
            String nickname = resolvedRequest.getNickname().trim();
            user.setNickname(StringUtils.hasText(nickname) ? nickname : user.getUsername());
            changed = true;
        }

        if (StringUtils.hasText(resolvedRequest.getNewPassword())) {
            String newPassword = resolvedRequest.getNewPassword().trim();
            if (newPassword.length() < AuthConstants.PASSWORD_MIN_LENGTH) {
                throw LearningAssistantException.badRequest(
                        LearningErrorCode.PASSWORD_TOO_SHORT,
                        "新密码至少 " + AuthConstants.PASSWORD_MIN_LENGTH + " 位");
            }
            if (!verifyPassword(resolvedRequest.getCurrentPassword(), user.getPasswordHash())) {
                throw LearningAssistantException.badRequest(
                        LearningErrorCode.PASSWORD_INCORRECT);
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

    /** 退出当前账户。 */
    public void logout() {
        LearningUser user = currentUserContext.requireUser();
        SecurityContextHolder.clearContext();
        log.info("用户「{}」退出登录", userDisplayNameService.displayName(user));
    }

    /**
     * 判断用户是否为系统管理员。
     */
    public boolean isAdmin(LearningUser user) {
        return user != null && UserRole.of(user.getRoleCode()) == UserRole.ADMIN;
    }

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

    private LearningUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getUsername, username)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

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

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String value = phone.trim();
        if (value.length() <= AuthConstants.PHONE_MASK_THRESHOLD) {
            return AuthConstants.CONTACT_MASK;
        }
        return value.substring(CommonConstants.ZERO, AuthConstants.PHONE_MASK_PREFIX_LENGTH)
                + AuthConstants.CONTACT_MASK
                + value.substring(value.length() - AuthConstants.PHONE_MASK_SUFFIX_LENGTH);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "";
        }
        String value = email.trim();
        int atIndex = value.indexOf('@');
        if (atIndex <= CommonConstants.ZERO) {
            return AuthConstants.CONTACT_MASK;
        }
        String name = value.substring(CommonConstants.ZERO, atIndex);
        String domain = value.substring(atIndex);
        if (name.length() <= AuthConstants.EMAIL_MASK_VISIBLE_PREFIX_LENGTH) {
            return name.charAt(CommonConstants.ZERO) + AuthConstants.CONTACT_MASK + domain;
        }
        return name.substring(CommonConstants.ZERO, AuthConstants.EMAIL_MASK_VISIBLE_PREFIX_LENGTH)
                + AuthConstants.CONTACT_MASK
                + domain;
    }

    private String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > AuthConstants.PHONE_MAX_LENGTH || !PHONE_PATTERN.matcher(value).matches()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.PHONE_INVALID,
                    "手机号码格式不正确");
        }
        return value;
    }

    private String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > AuthConstants.EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(value).matches()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.EMAIL_INVALID,
                    "联系邮箱格式不正确");
        }
        return value;
    }

    /** 为账户创建与登录校验一致的密码哈希。 */
    public String hashPassword(String password) {
        String salt = randomHex(AuthConstants.PASSWORD_SALT_BYTES);
        return PASSWORD_PREFIX + salt + "$" + sha256(salt + AuthConstants.PASSWORD_HASH_SEPARATOR + password);
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private boolean verifyPassword(String password, String passwordHash) {
        if (!StringUtils.hasText(passwordHash) || !passwordHash.startsWith(PASSWORD_PREFIX)) {
            return false;
        }
        String[] parts = passwordHash.split("\\$");
        if (parts.length != AuthConstants.PASSWORD_HASH_PART_COUNT) {
            return false;
        }
        return MessageDigest.isEqual(parts[AuthConstants.PASSWORD_DIGEST_PART_INDEX].getBytes(StandardCharsets.UTF_8),
                sha256(parts[AuthConstants.PASSWORD_SALT_PART_INDEX] + AuthConstants.PASSWORD_HASH_SEPARATOR + password)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.HASH_FAILED,
                    "密码哈希计算失败",
                    ex);
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

}
