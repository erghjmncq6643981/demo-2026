package com.chandler.fcc.server.outbound.application;

import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在写入通话意图前验证调度租约，阻止过期工作进程继续产生拨号副作用。 */
@Service
@RequiredArgsConstructor
public class DialAttemptGuard {
    private final DialJobMapper mapper;

    /** 锁定自动外呼尝试直到通话意图提交；人工呼叫不需要调度租约。
     * @param attemptId 自动外呼尝试标识，人工呼叫为空
     * @throws IllegalStateException 缺少事务或尝试已经失效
     */
    public void beforePersist(String attemptId) {
        if (attemptId == null) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("外呼派发校验必须与通话意图共用事务");
        }
        if (mapper.lockDispatch(attemptId) == null) {
            throw new IllegalStateException("外呼派发已过期或任务已取消");
        }
    }
}
