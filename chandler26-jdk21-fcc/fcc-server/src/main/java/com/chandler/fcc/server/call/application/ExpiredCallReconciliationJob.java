package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.call.domain.CallEndDecision;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallChannelFact;
import com.chandler.fcc.server.call.infrastructure.persistence.data.CallCleanupCandidate;
import com.chandler.fcc.server.call.infrastructure.persistence.mapper.CallEndMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 每日按数据库事实查找过期通话及收尾残留，网络对账与数据库写入分开执行。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiredCallReconciliationJob {
    private final CallEndMapper mapper;
    private final ChannelSnapshotReader snapshots;
    private final CallFinalizationService finalization;
    private final CallSessionManager sessions;
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${fcc.call-cleanup.enabled:true}")
    private boolean enabled = true;
    @Value("${fcc.call-cleanup.expire-hours:24}")
    private int expireHours = 24;
    @Value("${fcc.call-cleanup.page-size:100}")
    private int pageSize = 100;

    /** 每日北京时间 01:00 执行，可配置时区和 cron；重复触发不并行运行。 */
    @Scheduled(cron = "${fcc.call-cleanup.cron:0 0 1 * * *}", zone = "${fcc.call-cleanup.timezone:Asia/Shanghai}")
    public void reconcile() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        int scanned = 0;
        int repaired = 0;
        try {
            if (expireHours < 1 || pageSize < 1 || pageSize > 500) {
                throw new IllegalArgumentException("通话清理阈值须大于零，每页须为 1–500 条");
            }
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(expireHours);
            long after = 0;
            while (true) {
                List<CallCleanupCandidate> rows = mapper.candidates(after, cutoff, pageSize);
                if (rows.isEmpty()) break;
                scanned += rows.size();
                after = rows.getLast().getId();
                List<Long> ids = rows.stream().map(CallCleanupCandidate::getId).toList();
                Map<Long, List<CallChannelFact>> facts = mapper.channelFacts(ids).stream()
                    .collect(Collectors.groupingBy(CallChannelFact::getCallId));
                // 每页重新查快照，避免长时间复用旧快照；查询失败仍可使用完整源结束事实。
                Set<String> snapshot = snapshots.read();
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                List<CallEndDecision> decisions = new ArrayList<>();
                for (CallCleanupCandidate row : rows) {
                    CallEndPolicy.decide(row, facts.getOrDefault(row.getId(), List.of()), snapshot, now)
                        .ifPresent(decisions::add);
                }
                List<Long> completed = finalization.repair(decisions);
                repaired += completed.size();
                for (Long id : completed) {
                    // repair 返回时数据库事务已提交，再清理可重建的内存和 Redis 索引。
                    sessions.getByCallId(id.toString()).ifPresent(call -> sessions.removeSession(call.getCtrlId()));
                }
            }
            log.info("[通话日终对账] 执行完成 scanned={} repaired={} deferred={} expireHours={}",
                scanned, repaired, scanned - repaired, expireHours);
        } catch (RuntimeException failure) {
            log.error("[通话日终对账] 执行失败 scanned={} repaired={} type={}",
                scanned, repaired, failure.getClass().getSimpleName());
            log.debug("[通话日终对账] 失败诊断", failure);
        } finally {
            running.set(false);
        }
    }
}
