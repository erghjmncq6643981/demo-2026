package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.dto.auth.AuthRequest;
import com.chandler.motivation.domain.dto.auth.AuthResponse;
import com.chandler.motivation.domain.dto.auth.UserProfileUpdateRequest;
import com.chandler.motivation.domain.dto.auth.UserProfileResponse;
import com.chandler.motivation.domain.mapper.MotivationUserMapper;
import com.chandler.motivation.security.JwtClaims;
import com.chandler.motivation.security.JwtTokenService;
import com.chandler.motivation.security.MotivationUserPrincipal;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PASSWORD_PREFIX = "sha256$";
    private static final int SALT_BYTES = 16;

    private final MotivationUserMapper userMapper;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 注册家长账号并立即返回登录态。
     */
    public AuthResponse register(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (!StringUtils.hasText(username)) {
            throw new MotivationException("USERNAME_REQUIRED", "用户名不能为空");
        }
        if (!StringUtils.hasText(request.getPhoneNumber())) {
            throw new MotivationException("PHONE_REQUIRED", "手机号码不能为空");
        }
        if (!StringUtils.hasText(request.getInvitationCode())) {
            throw new MotivationException("INVITATION_CODE_REQUIRED", "邀请码不能为空");
        }
        if (findByUsername(username) != null) {
            throw new MotivationException("USER_ALREADY_EXISTS", "用户名已存在: " + username);
        }
        MotivationUser user = new MotivationUser();
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setUserType(MotivationEnums.UserType.PARENT.code());
        user.setEnabled(MotivationConstants.Flag.YES);
        user.setDeleted(MotivationConstants.Flag.NO);
        userMapper.insert(user);
        log.info("用户「{}」完成家长账号注册", user.getNickname());
        return createLoginResponse(user);
    }

    /**
     * 为孩子档案创建独立登录账号。
     */
    public MotivationUser createChildAccount(String username, String password, String nickname) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            throw new MotivationException("CHILD_USERNAME_REQUIRED", "孩子账号不能为空");
        }
        if (!StringUtils.hasText(password) || password.length() < 6 || password.length() > 64) {
            throw new MotivationException("CHILD_PASSWORD_INVALID", "孩子密码长度必须在 6 到 64 个字符之间");
        }
        if (findByUsername(normalizedUsername) != null) {
            throw new MotivationException("USER_ALREADY_EXISTS", "用户名已存在: " + normalizedUsername);
        }
        MotivationUser user = new MotivationUser();
        user.setUsername(normalizedUsername);
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : normalizedUsername);
        user.setPasswordHash(hashPassword(password));
        user.setUserType(MotivationEnums.UserType.CHILD.code());
        user.setEnabled(MotivationConstants.Flag.YES);
        user.setDeleted(MotivationConstants.Flag.NO);
        userMapper.insert(user);
        log.info("用户「{}」的孩子账号创建成功", user.getNickname());
        return user;
    }

    /**
     * 校验用户名和密码，成功后签发 JWT。
     */
    public AuthResponse login(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        MotivationUser user = findByUsername(username);
        if (user == null
                || !Integer.valueOf(MotivationConstants.Flag.YES).equals(user.getEnabled())
                || !Integer.valueOf(MotivationConstants.Flag.NO).equals(user.getDeleted())) {
            throw new MotivationException("AUTH_INVALID", "用户名或密码错误");
        }
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new MotivationException("AUTH_INVALID", "用户名或密码错误");
        }
        String userTypeName = MotivationEnums.descriptionOf(
                MotivationEnums.UserType.class,
                user.getUserType(),
                MotivationEnums.UserType.PARENT);
        log.info("{}用户「{}」登录成功", userTypeName, user.getNickname());
        return createLoginResponse(user);
    }

    public UserProfileResponse me() {
        return toProfile(requireUser());
    }

    /**
     * 更新当前登录用户的基础资料。
     */
    public UserProfileResponse updateProfile(UserProfileUpdateRequest request) {
        MotivationUser user = requireUser();
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (!StringUtils.hasText(nickname)) {
            throw new MotivationException("NICKNAME_REQUIRED", "昵称不能为空");
        }
        LambdaUpdateWrapper<MotivationUser> updateWrapper = new LambdaUpdateWrapper<MotivationUser>()
                .eq(MotivationUser::getId, user.getId())
                .set(MotivationUser::getNickname, nickname);
        if (request.getAvatarUrl() != null) {
            updateWrapper.set(MotivationUser::getAvatarUrl,
                    StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl().trim() : null);
        }
        userMapper.update(null, updateWrapper);
        MotivationUser latest = findByUsername(user.getUsername());
        log.info("用户「{}」更新了账号资料", latest.getNickname());
        return toProfile(latest);
    }

    public MotivationUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new MotivationException("AUTH_REQUIRED", "请先登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof MotivationUserPrincipal userPrincipal) {
            return userPrincipal.user();
        }
        throw new MotivationException("AUTH_REQUIRED", "请先登录");
    }

    private AuthResponse createLoginResponse(MotivationUser user) {
        String token = jwtTokenService.createToken(user.getId(), user.getUsername());
        JwtClaims claims = jwtTokenService.parse(token);
        MotivationUser update = new MotivationUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setExpiredTime(claims.expiredTime());
        response.setUser(toProfile(user));
        return response;
    }

    private MotivationUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<MotivationUser>()
                .eq(MotivationUser::getUsername, username)
                .last("limit 1"));
    }

    private UserProfileResponse toProfile(MotivationUser user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setUserType(user.getUserType());
        return response;
    }

    private String hashPassword(String password) {
        String salt = randomHex(SALT_BYTES);
        return PASSWORD_PREFIX + salt + "$" + sha256(salt + ":" + password);
    }

    private boolean verifyPassword(String password, String passwordHash) {
        if (!StringUtils.hasText(passwordHash) || !passwordHash.startsWith(PASSWORD_PREFIX)) {
            return false;
        }
        String[] parts = passwordHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        return MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                sha256(parts[1] + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new MotivationException("HASH_FAILED", "密码哈希计算失败");
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
