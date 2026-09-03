package com.chandler.learning.agent.vocabulary.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 词汇发音缺省定时检查与自动补全下载调度器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "learning.audio", name = "sync-enabled", havingValue = "true", matchIfMissing = true)
public class VocabularyAudioSyncScheduler {

    private final VocabularyAudioService vocabularyAudioService;

    @Value("${learning.audio.sync-throttle-ms:50}")
    private long throttleMs;

    /**
     * 定时扫描词本与缓存词卡的发音音频，缺省自动从远程词典下载补全。
     */
    @Scheduled(cron = "${learning.audio.sync-cron:0 0 3 * * ?}")
    public void scheduleSyncMissingAudio() {
        log.info("开始执行词汇发音缺省定时检查与补全任务...");
        syncMissingAudio();
    }

    /**
     * 执行词汇发音缺省扫描与补全（支持手动触发与单测调用）。
     *
     * @return 统计结果对象
     */
    public AudioSyncResult syncMissingAudio() {
        long startTime = System.currentTimeMillis();
        Set<String> terms = vocabularyAudioService.collectAllVocabularyTerms();
        int totalTerms = terms.size();
        int missingCount = 0;
        int downloadedCount = 0;

        log.info("已扫描词汇库待核验发音总词数: {}", totalTerms);

        for (String term : terms) {
            try {
                int count = vocabularyAudioService.syncEnsureAudio(term);
                if (count > 0) {
                    missingCount++;
                    downloadedCount += count;
                    if (throttleMs > 0) {
                        Thread.sleep(throttleMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("词汇发音定时同步任务被中断");
                break;
            } catch (Exception ex) {
                log.warn("核验/下载词汇发音异常 term={}: {}", term, ex.getMessage());
            }
        }

        long costMs = System.currentTimeMillis() - startTime;
        log.info("词汇发音缺省检查与补全任务执行完毕: 扫描总词数={}, 存在缺省词数={}, 成功补全音频文件数={}, 总耗时={}ms",
                totalTerms, missingCount, downloadedCount, costMs);

        return new AudioSyncResult(totalTerms, missingCount, downloadedCount, costMs);
    }

    /**
     * 同步统计结果。
     */
    public record AudioSyncResult(int totalTerms, int missingTerms, int downloadedFiles, long costMs) {}
}
