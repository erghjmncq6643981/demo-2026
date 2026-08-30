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

    /** 获取当前安全上下文中的用户 ID。 */
    public Long currentUserId() {
        return currentUserContext.findUser()
                .map(user -> user.getId())
                .orElse(PersistenceConstants.SYSTEM_USER_ID);
    }
}
