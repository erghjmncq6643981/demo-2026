package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.learning.AuthRequest;
import com.chandler.learning.agent.domain.dto.learning.AuthResponse;
import com.chandler.learning.agent.domain.dto.learning.UserProfileResponse;
import com.chandler.learning.agent.domain.dto.learning.UserProfileUpdateRequest;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.mapper.learning.LearningUserMapper;
import com.chandler.learning.agent.security.JwtClaims;
import com.chandler.learning.agent.security.JwtTokenService;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PASSWORD_PREFIX = "sha256$";

    private final LearningUserMapper userMapper;
    private final WordbookService wordbookService;
    private final JwtTokenService jwtTokenService;
    private final SystemLogService systemLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthResponse register(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        LearningUser existing = findByUsername(username);
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        LocalDateTime now = LocalDateTime.now();
        LearningUser user = new LearningUser();
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setEnabled(true);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);
        wordbookService.ensureDefaultWordbook(user.getId());
        systemLogService.record(user.getId(), "auth", "注册成功", username);
        return createLoginResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        LearningUser user = findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        wordbookService.ensureDefaultWordbook(user.getId());
        systemLogService.record(user.getId(), "auth", "登录成功", username);
        return createLoginResponse(user);
    }

    public UserProfileResponse me(String authorization) {
        return toProfile(requireUser(authorization));
    }

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
            if (newPassword.length() < 6) {
                throw new IllegalArgumentException("新密码至少 6 位");
            }
            if (!verifyPassword(resolvedRequest.getCurrentPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("当前密码不正确");
            }
            user.setPasswordHash(hashPassword(newPassword));
            changed = true;
        }

        if (changed) {
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
            systemLogService.record(user.getId(), "auth", "更新账户信息", user.getUsername());
        }
        return toProfile(user);
    }

    public void logout(String authorization) {
        SecurityContextHolder.clearContext();
    }

    public LearningUser requireUser(String authorization) {
        LearningUser contextUser = currentSecurityUser();
        if (contextUser != null) {
            return contextUser;
        }
        String token = resolveToken(authorization);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("请先登录");
        }
        JwtClaims claims;
        try {
            claims = jwtTokenService.parse(token);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("登录已过期，请重新登录");
        }
        LearningUser user = userMapper.selectById(claims.userId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("用户不可用");
        }
        return user;
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
        return response;
    }

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

    private LearningUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<LearningUser>()
                .eq(LearningUser::getUsername, username)
                .last("LIMIT 1"));
    }

    private UserProfileResponse toProfile(LearningUser user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        return response;
    }

    private String hashPassword(String password) {
        String salt = randomHex(16);
        return PASSWORD_PREFIX + salt + "$" + sha256(salt + ":" + password);
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
        if (parts.length != 3) {
            return false;
        }
        return MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                sha256(parts[1] + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String resolveToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }
        String value = authorization.trim();
        return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : value;
    }
}
