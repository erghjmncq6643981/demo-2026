package com.chandler.learning.agent.security;

import com.chandler.learning.agent.common.constant.PersistenceConstants;
import org.springframework.stereotype.Component;

/**
 * 当前操作人解析器。
 * <p>
 * 数据审计字段优先使用登录用户 ID；在启动初始化、测试或匿名上下文中回退为系统用户。
 */
@Component
public class LearningAuditor {

    private final CurrentUserContext currentUserContext;

    public LearningAuditor(CurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    /**
     * 处理 {@code currentUserId} 相关业务。
     */
    public Long currentUserId() {
        return currentUserContext.findUser()
                .map(user -> user.getId())
                .orElse(PersistenceConstants.SYSTEM_USER_ID);
    }
}
