package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 词汇发音与场景材料 AI 真人朗读音频缺省定时检查与自动补全调度器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "learning.audio", name = "sync-enabled", havingValue = "true", matchIfMissing = true)
public class VocabularyAudioSyncScheduler {

    private final VocabularyAudioService vocabularyAudioService;
    private final SceneArticleAudioService sceneArticleAudioService;

    @Value("${learning.audio.sync-throttle-ms:50}")
    private long throttleMs;

    /**
     * 定时扫描词汇发音与场景文章 AI 真人朗读音频，缺省自动从远程源补全。
     */
    @Scheduled(cron = "${learning.audio.sync-cron:0 0 3 * * ?}")
    public void scheduleSyncMissingAudio() {
        log.info("开始执行词汇与场景文章音频缺省定时检查与补全任务...");
        syncMissingAudio();
    }

    /**
     * 执行词汇发音与场景文章音频缺省扫描与补全（支持手动触发与单测调用）。
     *
     * @return 统计结果对象
     */
    public AudioSyncResult syncMissingAudio() {
        long startTime = System.currentTimeMillis();

        // 1. 词汇发音缺省检查与补全（有道词典）
        Set<String> terms = vocabularyAudioService.collectAllVocabularyTerms();
        int totalTerms = terms.size();
        int missingTerms = 0;
        int downloadedFiles = 0;

        log.info("【音频同步】已扫描词汇库待核验发音总词数: {}", totalTerms);
        for (String term : terms) {
            try {
                int count = vocabularyAudioService.syncEnsureAudio(term);
                if (count > 0) {
                    missingTerms++;
                    downloadedFiles += count;
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

        // 2. 场景材料 AI 真人朗读音频缺省检查与补全（阿里云 TTS）
        List<Long> sceneUnitIds = sceneArticleAudioService.collectAllSceneUnitIds();
        int totalSceneUnits = sceneUnitIds.size();
        int missingSceneUnits = 0;
        int synthesizedSceneAudio = 0;

        log.info("【音频同步】已扫描场景文章待核验 AI 朗读单元数: {}", totalSceneUnits);
        for (Long unitId : sceneUnitIds) {
            try {
                int count = sceneArticleAudioService.syncEnsureSceneAudio(unitId);
                if (count > 0) {
                    missingSceneUnits++;
                    synthesizedSceneAudio += count;
                    if (throttleMs > 0) {
                        Thread.sleep(throttleMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("场景文章语音定时同步任务被中断");
                break;
            } catch (Exception ex) {
                log.warn("核验/合成场景文章语音异常 unitId={}: {}", unitId, ex.getMessage());
            }
        }

        long costMs = System.currentTimeMillis() - startTime;
        log.info("音频缺省检查与补全任务执行完毕: 词汇[扫描={}, 缺省={}, 补全文件={}], 场景文章[扫描={}, 缺省={}, 成功合成={}], 总耗时={}ms",
                totalTerms, missingTerms, downloadedFiles,
                totalSceneUnits, missingSceneUnits, synthesizedSceneAudio, costMs);

        return new AudioSyncResult(
                totalTerms, missingTerms, downloadedFiles,
                totalSceneUnits, missingSceneUnits, synthesizedSceneAudio,
                costMs);
    }

    /**
     * 同步统计结果。
     */
    public record AudioSyncResult(
            int totalTerms, int missingTerms, int downloadedFiles,
            int totalSceneUnits, int missingSceneUnits, int synthesizedSceneAudio,
            long costMs) {}
}
