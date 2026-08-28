package com.chandler.learning.agent.identity.application;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.identity.infrastructure.mapper.LearningUserMapper;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将用户 ID 转成业务日志里可读的操作者名称。
 */
@Service
@RequiredArgsConstructor
public class UserDisplayNameService {

    private final LearningUserMapper userMapper;

    /**
     * 处理 {@code currentUserName} 相关业务。
     */
    public String currentUserName() {
        LearningUser currentUser = currentUser();
        return currentUser == null ? "系统" : displayName(currentUser);
    }

    /**
     * 处理 {@code userName} 相关业务。
     */
    public String userName(Long userId) {
        if (userId == null) {
            return currentUserName();
        }
        LearningUser currentUser = currentUser();
        if (currentUser != null && userId.equals(currentUser.getId())) {
            return displayName(currentUser);
        }
        LearningUser user = userMapper.selectById(userId);
        return user == null ? "用户#" + userId : displayName(user);
    }

    /** 一次加载多个用户名称，供管理列表等批量响应避免逐行查询用户表。 */
    public Map<Long, String> userNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        LearningUser currentUser = currentUser();
        if (currentUser != null) {
            result.put(currentUser.getId(), displayName(currentUser));
        }
        userMapper.selectBatchIds(userIds.stream().filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(user -> result.put(user.getId(), displayName(user)));
        userIds.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> result.putIfAbsent(id, "用户#" + id));
        return Map.copyOf(result);
    }

    /**
     * 处理 {@code displayName} 相关业务。
     */
    public String displayName(LearningUser user) {
        if (user == null) {
            return "系统";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return "用户#" + user.getId();
    }

    /**
     * 处理 {@code currentUser} 相关业务。
     */
    private LearningUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof LearningUserPrincipal principal) {
            return principal.user();
        }
        return null;
    }
}
