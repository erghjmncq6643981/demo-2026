package com.chandler.learning.agent.security;

import com.chandler.learning.agent.support.LearningConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前操作人解析器。
 * <p>
 * 数据审计字段优先使用登录用户 ID；在启动初始化、测试或匿名上下文中回退为系统用户。
 */
@Component
public class LearningAuditor {

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return LearningConstants.Audit.SYSTEM_USER_ID;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LearningUserPrincipal learningUserPrincipal
                && learningUserPrincipal.user() != null
                && learningUserPrincipal.user().getId() != null) {
            return learningUserPrincipal.user().getId();
        }
        return LearningConstants.Audit.SYSTEM_USER_ID;
    }
}
