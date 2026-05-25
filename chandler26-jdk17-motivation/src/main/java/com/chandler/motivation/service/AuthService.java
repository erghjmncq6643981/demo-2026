package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.motivation.common.exception.MotivationException;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.dto.auth.AuthRequest;
import com.chandler.motivation.domain.dto.auth.AuthResponse;
import com.chandler.motivation.domain.dto.auth.UserProfileResponse;
import com.chandler.motivation.domain.mapper.MotivationUserMapper;
import com.chandler.motivation.security.JwtClaims;
import com.chandler.motivation.security.JwtTokenService;
import com.chandler.motivation.security.MotivationUserPrincipal;
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

    public AuthResponse register(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (!StringUtils.hasText(username)) {
            throw new MotivationException("USERNAME_REQUIRED", "用户名不能为空");
        }
        if (findByUsername(username) != null) {
            throw new MotivationException("USER_ALREADY_EXISTS", "用户名已存在: " + username);
        }
        MotivationUser user = new MotivationUser();
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPasswordHash(hashPassword(request.getPassword()));
        user.setUserType("PARENT");
        user.setEnabled(1);
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("家长用户「{}」完成注册", user.getNickname());
        return createLoginResponse(user);
    }

    public AuthResponse login(AuthRequest request) {
        String username = normalizeUsername(request.getUsername());
        MotivationUser user = findByUsername(username);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled()) || !Integer.valueOf(0).equals(user.getDeleted())) {
            throw new MotivationException("AUTH_INVALID", "用户名或密码错误");
        }
        if (!verifyPassword(request.getPassword(), user.getPasswordHash())) {
            throw new MotivationException("AUTH_INVALID", "用户名或密码错误");
        }
        log.info("家长用户「{}」登录成功", user.getNickname());
        return createLoginResponse(user);
    }

    public UserProfileResponse me() {
        return toProfile(requireUser());
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
