package com.chandler.learning.agent.service.learning;

import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.mapper.learning.LearningUserMapper;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将用户 ID 转成业务日志里可读的操作者名称。
 */
@Service
@RequiredArgsConstructor
public class UserDisplayNameService {

    private final LearningUserMapper userMapper;

    public String currentUserName() {
        LearningUser currentUser = currentUser();
        return currentUser == null ? "系统" : displayName(currentUser);
    }

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
