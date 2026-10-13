package com.chandler.fcc.server.outbound.application;

import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 通话收尾调用的窄外呼结果契约，复用调度器的成功条件及重试规则。 */
@Service
@RequiredArgsConstructor
public class DialJobReconciliationService {
    private final DialJobMapper mapper;

    /**
     * 回填指定已结束通话的运行中尝试；已成功项不会再次执行。
     * @param callIds 非空通话主键列表
     */
    public void reconcileEndedCalls(List<Long> callIds) {
        if (callIds.isEmpty()) return;
        List<String> attempts = mapper.attemptsForCalls(callIds);
        if (!attempts.isEmpty()) mapper.reconcileBatch(attempts);
    }
}
